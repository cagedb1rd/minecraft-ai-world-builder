package dev.worldbuilder.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionLifecycleTest {
    @Test void supportsCommitUndoRedoAndRejectsInvalidTransitions() {
        var undone = TransactionLifecycle.afterUndo(TransactionManager.Status.COMMITTED);
        assertEquals(TransactionManager.Status.UNDONE, undone);
        assertThrows(BridgeOperationException.class, () -> TransactionLifecycle.afterUndo(undone));
        var redone = TransactionLifecycle.afterRedo(undone);
        assertEquals(TransactionManager.Status.COMMITTED, redone);
        assertThrows(BridgeOperationException.class, () -> TransactionLifecycle.afterRedo(redone));
        assertEquals(TransactionManager.Status.UNDONE, TransactionLifecycle.afterUndo(TransactionManager.Status.CANCELLED));
    }
}
