package dev.worldbuilder.bridge;

final class TransactionLifecycle {
    static TransactionManager.Status afterUndo(TransactionManager.Status current) {
        if (current != TransactionManager.Status.COMMITTED && current != TransactionManager.Status.CANCELLED) throw new BridgeOperationException("TRANSACTION_FAILED", "Transaction is not undoable");
        return TransactionManager.Status.UNDONE;
    }
    static TransactionManager.Status afterRedo(TransactionManager.Status current) {
        if (current != TransactionManager.Status.UNDONE) throw new BridgeOperationException("TRANSACTION_FAILED", "Transaction is not undone");
        return TransactionManager.Status.COMMITTED;
    }
    private TransactionLifecycle() {}
}
