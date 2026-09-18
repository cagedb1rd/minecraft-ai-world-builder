import { blockStateSchema, type Operation } from "@minecraft-ai-builder/shared-protocol";
import * as z from "zod/v4";
import type { ArchitecturalDesign, PaletteIntent } from "./architectural.js";

export type BridgeQueryClient = { call(operation: Operation, args?: Record<string, unknown>): Promise<unknown> };
export const paletteSelectionSchema = z.object({
  role: z.string(),
  state: blockStateSchema,
  score: z.number(),
  reason: z.string(),
  candidatesConsidered: z.number().int().nonnegative(),
  fallbackFrom: z.string().optional()
});
export type PaletteSelection = z.infer<typeof paletteSelectionSchema>;

export type ResolvedPalette = {
  palette: Record<string, z.infer<typeof blockStateSchema>>;
  selections: PaletteSelection[];
  warnings: string[];
  cacheKey: string;
};

type BlockCandidate = { id: string; namespace?: string; displayName?: string; hasItem?: boolean; hasBlockEntity?: boolean; tags?: string[]; properties?: Record<string, string[]> };

function asCandidates(value: unknown): BlockCandidate[] {
  if (Array.isArray(value)) return value.filter(item => item && typeof item === "object" && typeof (item as { id?: unknown }).id === "string") as BlockCandidate[];
  if (value && typeof value === "object" && Array.isArray((value as { blocks?: unknown }).blocks)) return asCandidates((value as { blocks: unknown }).blocks);
  return [];
}

function text(candidate: BlockCandidate): string { return `${candidate.id} ${candidate.displayName ?? ""} ${candidate.namespace ?? ""}`.toLowerCase(); }
function includesAny(value: string, needles: string[]): boolean { return needles.length === 0 || needles.some(needle => value.includes(needle.toLowerCase())); }
function propertyMatches(candidate: BlockCandidate, desired: Record<string, string>): boolean { return Object.entries(desired).every(([key, value]) => Boolean(candidate.properties?.[key]?.includes(value))); }

export class PaletteResolver {
  private readonly cache = new Map<string, ResolvedPalette>();
  constructor(private readonly bridge: BridgeQueryClient) {}

  clearCache(): void { this.cache.clear(); }

  async resolve(design: ArchitecturalDesign): Promise<ResolvedPalette> {
    const cacheKey = `${await this.environmentKey()}|${JSON.stringify(design.paletteIntents)}`;
    const cached = this.cache.get(cacheKey);
    if (cached && Object.keys(design.paletteIntents).every(role => role in cached.palette)) return cached;
    const palette: Record<string, z.infer<typeof blockStateSchema>> = {};
    const selections: PaletteSelection[] = [];
    const warnings: string[] = [];
    const resolving = new Set<string>();
    for (const role of Object.keys(design.paletteIntents)) {
      const intent = design.paletteIntents[role];
      if (!intent) continue;
      const result = await this.resolveRole(role, intent, design.paletteIntents, palette, selections, warnings, resolving);
      if (!result) throw new Error(`PALETTE_ROLE_UNRESOLVED: ${role}`);
    }
    const resolved = { palette, selections, warnings, cacheKey };
    this.cache.set(cacheKey, resolved);
    return resolved;
  }

  async validate(palette: Record<string, unknown>): Promise<{ valid: boolean; errors: string[] }> {
    const errors: string[] = [];
    for (const [role, state] of Object.entries(palette)) {
      const result = blockStateSchema.safeParse(state);
      if (!result.success) errors.push(`${role}: invalid BlockState`);
      else {
        try {
          const info = await this.bridge.call("get_block_states", { id: result.data.id });
          const properties = info && typeof info === "object" ? (info as Record<string, unknown>).properties : undefined;
          if (properties && typeof properties === "object") for (const [key, value] of Object.entries(result.data.properties)) {
            const allowed = (properties as Record<string, unknown>)[key];
            if (!Array.isArray(allowed) || !allowed.includes(value)) errors.push(`${role}: invalid ${key}=${value} for ${result.data.id}`);
          }
        }
        catch (error) { errors.push(`${role}: block ${result.data.id} is not present in the current Registry`); }
      }
    }
    return { valid: errors.length === 0, errors };
  }

  private async environmentKey(): Promise<string> {
    const [world, mods, capabilities] = await Promise.all([
      this.bridge.call("get_world_info").catch(() => ({})),
      this.bridge.call("list_mods").catch(() => []),
      this.bridge.call("get_mod_capabilities").catch(() => ({}))
    ]);
    return JSON.stringify({ world: typeof world === "object" && world ? (world as Record<string, unknown>).minecraftVersion ?? "unknown" : "unknown", mods, capabilities });
  }

  private async resolveRole(role: string, intent: PaletteIntent, intents: Record<string, PaletteIntent>, palette: Record<string, z.infer<typeof blockStateSchema>>, selections: PaletteSelection[], warnings: string[], resolving: Set<string>): Promise<z.infer<typeof blockStateSchema> | undefined> {
    if (palette[role]) return palette[role];
    if (resolving.has(role)) throw new Error(`PALETTE_FALLBACK_CYCLE: ${role}`);
    resolving.add(role);
    try {
      const candidates = await this.collectCandidates(role, intent);
      const ranked = candidates.map(candidate => ({ candidate, score: this.score(candidate, intent) })).filter(item => !intent.excludedKeywords.some(word => text(item.candidate).includes(word.toLowerCase()))).sort((a, b) => b.score - a.score || a.candidate.id.localeCompare(b.candidate.id));
      const selected = ranked.find(item => item.score >= this.minimumScore(intent));
      if (selected) {
        const state = { id: selected.candidate.id, properties: { ...intent.requiredProperties } };
        if (await this.stateExists(state)) {
          palette[role] = state;
          selections.push({ role, state, score: selected.score, reason: this.reason(selected.candidate, intent, selected.score), candidatesConsidered: ranked.length });
          return state;
        }
      }
      for (const fallback of intent.fallbackRoles) {
        if (!intents[fallback]) continue;
        const inherited = await this.resolveRole(fallback, intents[fallback], intents, palette, selections, warnings, resolving);
        if (inherited) {
          palette[role] = inherited;
          selections.push({ role, state: inherited, score: 0, reason: `Fallback to palette role ${fallback}`, candidatesConsidered: ranked.length, fallbackFrom: fallback });
          warnings.push(`Palette role ${role} used fallback role ${fallback}`);
          return inherited;
        }
      }
      if (intent.allowVanillaFallback) {
        const vanilla = await this.collectCandidates(role, { ...intent, preferredNamespaces: ["minecraft"], requiredKeywords: [], preferredKeywords: intent.preferredKeywords.length ? intent.preferredKeywords : [role], excludedKeywords: [] });
        const candidate = vanilla.sort((a, b) => a.id.localeCompare(b.id))[0];
        if (candidate) {
          const state = { id: candidate.id, properties: { ...intent.requiredProperties } };
          if (await this.stateExists(state)) {
            palette[role] = state;
            selections.push({ role, state, score: 0, reason: "Vanilla fallback explicitly allowed", candidatesConsidered: vanilla.length });
            warnings.push(`Palette role ${role} used vanilla fallback ${candidate.id}`);
            return state;
          }
        }
      }
      return undefined;
    } finally { resolving.delete(role); }
  }

  private async collectCandidates(role: string, intent: PaletteIntent): Promise<BlockCandidate[]> {
    const queries = [...new Set([role, ...intent.requiredKeywords, ...intent.preferredKeywords])].filter(Boolean);
    const all = new Map<string, BlockCandidate>();
    for (const id of intent.preferredIds) {
      try { const info = await this.bridge.call("get_block_info", { id }); const candidates = asCandidates([info]); for (const candidate of candidates) all.set(candidate.id, candidate); }
      catch { /* candidate is ignored; Registry is authoritative */ }
    }
    for (const query of queries) {
      try { for (const candidate of asCandidates(await this.bridge.call("search_blocks", { query, limit: 100 }))) all.set(candidate.id, candidate); }
      catch { /* an unavailable query should not hide other candidates */ }
    }
    const enriched: BlockCandidate[] = [];
    for (const candidate of all.values()) {
      if (candidate.tags && candidate.properties) enriched.push(candidate);
      else {
        try {
          const info = await this.bridge.call("get_block_info", { id: candidate.id });
          const details = info && typeof info === "object" ? info as Record<string, unknown> : {};
          const tags = Array.isArray(details.tags) ? details.tags.filter((tag): tag is string => typeof tag === "string") : undefined;
          const properties = details.properties && typeof details.properties === "object" ? details.properties as Record<string, string[]> : undefined;
          enriched.push({ ...candidate, ...(tags ? { tags } : {}), ...(properties ? { properties } : {}) });
        } catch { enriched.push(candidate); }
      }
    }
    return enriched;
  }

  private score(candidate: BlockCandidate, intent: PaletteIntent): number {
    const value = text(candidate);
    let score = candidate.hasItem === false ? -50 : 10;
    if (candidate.hasBlockEntity) score -= 20;
    if (intent.preferredIds.includes(candidate.id)) score += 100;
    if (intent.preferredNamespaces.includes(candidate.namespace ?? candidate.id.split(":")[0] ?? "")) score += 20;
    if (intent.requiredKeywords.length && intent.requiredKeywords.every(word => value.includes(word.toLowerCase()))) score += 35;
    else if (intent.requiredKeywords.length) score -= 50;
    if (intent.requiredTags.length && intent.requiredTags.every(tag => candidate.tags?.includes(tag))) score += 40;
    else if (intent.requiredTags.length) score -= 60;
    score += intent.preferredKeywords.filter(word => value.includes(word.toLowerCase())).length * 10;
    score += intent.preferredTags.filter(tag => candidate.tags?.includes(tag)).length * 12;
    if (!propertyMatches(candidate, intent.requiredProperties)) score -= 40;
    score += Object.entries(intent.preferredProperties).filter(([key, value]) => candidate.properties?.[key]?.includes(value)).length * 5;
    return score;
  }

  private minimumScore(intent: PaletteIntent): number {
    // An unconstrained role must not silently select the first registry block;
    // it must declare a selector or an explicit fallback instead.
    return intent.requiredKeywords.length || intent.requiredTags.length || intent.preferredIds.length || intent.preferredKeywords.length || intent.preferredNamespaces.length || Object.keys(intent.requiredProperties).length ? 10 : 11;
  }
  private async stateExists(state: z.infer<typeof blockStateSchema>): Promise<boolean> {
    try {
      const info = await this.bridge.call("get_block_states", { id: state.id });
      if (!info || typeof info !== "object") return false;
      const properties = (info as Record<string, unknown>).properties;
      if (!properties || typeof properties !== "object") return Object.keys(state.properties).length === 0;
      return Object.entries(state.properties).every(([key, value]) => {
        const values = (properties as Record<string, unknown>)[key];
        return Array.isArray(values) && values.includes(value);
      });
    } catch {
      return false;
    }
  }
  private reason(candidate: BlockCandidate, intent: PaletteIntent, score: number): string { return `Selected ${candidate.id} with score ${score}; matched preferred keywords/tags and passed BlockState constraints`; }
}
