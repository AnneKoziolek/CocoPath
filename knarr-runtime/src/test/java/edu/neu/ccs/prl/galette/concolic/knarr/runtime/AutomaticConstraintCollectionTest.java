package edu.neu.ccs.prl.galette.concolic.knarr.runtime;

import static org.junit.jupiter.api.Assertions.*;

import edu.neu.ccs.prl.galette.internal.runtime.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.ac.sun.cs.green.expr.*;

/**
 * Test automatic path constraint collection from branch and switch statements.
 *
 * <p>This test verifies that the runtime support methods in PathUtils correctly
 * collect constraints when called by bytecode instrumentation.
 *
 * <p><b>Note</b>: Full bytecode instrumentation testing requires running with
 * Galette agent. These tests verify the runtime methods work correctly when called.
 */
class AutomaticConstraintCollectionTest {

    @BeforeEach
    void setUp() {
        // Reset PathUtils state before each test
        PathUtils.reset();
        GaletteSymbolicator.reset();
    }

    @AfterEach
    void tearDown() {
        PathUtils.reset();
        GaletteSymbolicator.reset();
    }

    @Test
    void testRecordBranchConstraint_IFEQ_Taken() {
        // Create a symbolic integer variable
        Tag symbolicTag = GaletteSymbolicator.makeSymbolicInt("x", 0);

        // Simulate IFEQ (if equal to zero) with branch taken
        PathUtils.recordBranchConstraint(symbolicTag, 153, true); // 153 = IFEQ opcode

        // Verify constraint was recorded: x == 0
        PathConditionWrapper pc = PathUtils.getCurPC();
        assertNotNull(pc);
        assertFalse(pc.isEmpty());

        Expression constraint = pc.toSingleExpression();
        assertNotNull(constraint);

        // The constraint should be: x == 0
        assertTrue(constraint instanceof BinaryOperation);
        BinaryOperation binOp = (BinaryOperation) constraint;
        assertEquals(Operation.Operator.EQ, binOp.getOperator());
    }

    @Test
    void testRecordBranchConstraint_IFEQ_NotTaken() {
        // Create a symbolic integer variable
        Tag symbolicTag = GaletteSymbolicator.makeSymbolicInt("x", 5);

        // Simulate IFEQ (if equal to zero) with branch NOT taken
        PathUtils.recordBranchConstraint(symbolicTag, 153, false); // 153 = IFEQ opcode

        // Verify constraint was recorded: x != 0
        PathConditionWrapper pc = PathUtils.getCurPC();
        Expression constraint = pc.toSingleExpression();

        assertTrue(constraint instanceof BinaryOperation);
        BinaryOperation binOp = (BinaryOperation) constraint;
        assertEquals(Operation.Operator.NE, binOp.getOperator());
    }

    @Test
    void testRecordBranchConstraint_IFGT_Taken() {
        // Create a symbolic integer variable
        Tag symbolicTag = GaletteSymbolicator.makeSymbolicInt("y", 10);

        // Simulate IFGT (if greater than zero) with branch taken
        PathUtils.recordBranchConstraint(symbolicTag, 157, true); // 157 = IFGT opcode

        // Verify constraint was recorded: y > 0
        PathConditionWrapper pc = PathUtils.getCurPC();
        Expression constraint = pc.toSingleExpression();

        assertTrue(constraint instanceof BinaryOperation);
        BinaryOperation binOp = (BinaryOperation) constraint;
        assertEquals(Operation.Operator.GT, binOp.getOperator());
    }

    @Test
    void testRecordBranchConstraint_IFGT_NotTaken() {
        // Create a symbolic integer variable
        Tag symbolicTag = GaletteSymbolicator.makeSymbolicInt("y", -5);

        // Simulate IFGT (if greater than zero) with branch NOT taken
        PathUtils.recordBranchConstraint(symbolicTag, 157, false); // 157 = IFGT opcode

        // Verify constraint was recorded: y <= 0
        PathConditionWrapper pc = PathUtils.getCurPC();
        Expression constraint = pc.toSingleExpression();

        assertTrue(constraint instanceof BinaryOperation);
        BinaryOperation binOp = (BinaryOperation) constraint;
        assertEquals(Operation.Operator.LE, binOp.getOperator());
    }

    @Test
    void testRecordBranchConstraint_NoSymbolicTag() {
        // Call with null tag (concrete value, no symbolic tag)
        PathUtils.recordBranchConstraint(null, 153, true);

        // Verify no constraint was recorded
        PathConditionWrapper pc = PathUtils.getCurPC();
        assertTrue(pc.isEmpty(), "No constraint should be recorded for concrete values");
    }

    @Test
    void testRecordSwitchConstraintAuto_Case0() {
        // Create a symbolic integer for switch index
        Tag symbolicTag = GaletteSymbolicator.makeSymbolicInt("choice", 0);

        // Simulate selecting case 0
        PathUtils.recordSwitchConstraintAuto(symbolicTag, 0);

        // Verify constraint was recorded: choice == 0
        PathConditionWrapper pc = PathUtils.getCurPC();
        Expression constraint = pc.toSingleExpression();

        assertTrue(constraint instanceof BinaryOperation);
        BinaryOperation binOp = (BinaryOperation) constraint;
        assertEquals(Operation.Operator.EQ, binOp.getOperator());

        // Right operand should be IntConstant(0)
        assertTrue(binOp.right instanceof IntConstant);
        // Note: IntConstant.getValue() throws UnsupportedOperationException in Green solver
        // Just verify the type is correct
    }

    @Test
    void testRecordSwitchConstraintAuto_Case2() {
        // Create a symbolic integer for switch index
        Tag symbolicTag = GaletteSymbolicator.makeSymbolicInt("choice", 2);

        // Simulate selecting case 2
        PathUtils.recordSwitchConstraintAuto(symbolicTag, 2);

        // Verify constraint was recorded: choice == 2
        PathConditionWrapper pc = PathUtils.getCurPC();
        Expression constraint = pc.toSingleExpression();

        assertTrue(constraint instanceof BinaryOperation);
        BinaryOperation binOp = (BinaryOperation) constraint;
        assertEquals(Operation.Operator.EQ, binOp.getOperator());

        assertTrue(binOp.right instanceof IntConstant);
        // Note: IntConstant.getValue() throws UnsupportedOperationException in Green solver
        // Just verify the type is correct
    }

    @Test
    void testRecordSwitchConstraintAuto_DefaultCase() {
        // Create a symbolic integer for switch index
        Tag symbolicTag = GaletteSymbolicator.makeSymbolicInt("choice", 99);

        // Simulate selecting default case (-1 indicates default)
        PathUtils.recordSwitchConstraintAuto(symbolicTag, -1);

        // Currently, default case handling is not implemented (returns early)
        // Verify no constraint was recorded
        PathConditionWrapper pc = PathUtils.getCurPC();
        assertTrue(pc.isEmpty(), "Default case constraint recording not yet implemented");
    }

    @Test
    void testMultipleBranchConstraints() {
        // Create symbolic variables
        Tag tagX = GaletteSymbolicator.makeSymbolicInt("x", 5);
        Tag tagY = GaletteSymbolicator.makeSymbolicInt("y", 10);

        // Record multiple branch constraints
        PathUtils.recordBranchConstraint(tagX, 157, true); // x > 0
        PathUtils.recordBranchConstraint(tagY, 155, false); // y >= 0 (IFLT not taken)

        // Verify both constraints were recorded
        PathConditionWrapper pc = PathUtils.getCurPC();
        assertEquals(2, pc.size(), "Should have 2 constraints");

        Expression combined = pc.toSingleExpression();
        assertNotNull(combined);

        // Should be an AND of the two constraints
        assertTrue(combined instanceof BinaryOperation);
        BinaryOperation and = (BinaryOperation) combined;
        assertEquals(Operation.Operator.AND, and.getOperator());
    }

    @Test
    void testMixedManualAndAutomaticConstraints() {
        // Create symbolic variable
        Tag symbolicTag = GaletteSymbolicator.makeSymbolicInt("value", 3);

        // Add manual domain constraint
        PathUtils.addIntDomainConstraint("value", 0, 10);

        // Add automatic switch constraint
        PathUtils.recordSwitchConstraintAuto(symbolicTag, 3);

        // Verify both constraints were recorded
        PathConditionWrapper pc = PathUtils.getCurPC();
        assertTrue(pc.size() >= 2, "Should have at least 2 constraints (domain + switch)");

        Expression combined = pc.toSingleExpression();
        assertNotNull(combined);
    }

    @Test
    void testAllBranchOpcodes() {
        // Test all supported branch opcodes
        Tag tag = GaletteSymbolicator.makeSymbolicInt("test", 0);

        // Reset and test each opcode
        PathUtils.reset();
        PathUtils.recordBranchConstraint(tag, 153, true); // IFEQ
        assertFalse(PathUtils.getCurPC().isEmpty());

        PathUtils.reset();
        PathUtils.recordBranchConstraint(tag, 154, true); // IFNE
        assertFalse(PathUtils.getCurPC().isEmpty());

        PathUtils.reset();
        PathUtils.recordBranchConstraint(tag, 155, true); // IFLT
        assertFalse(PathUtils.getCurPC().isEmpty());

        PathUtils.reset();
        PathUtils.recordBranchConstraint(tag, 156, true); // IFGE
        assertFalse(PathUtils.getCurPC().isEmpty());

        PathUtils.reset();
        PathUtils.recordBranchConstraint(tag, 157, true); // IFGT
        assertFalse(PathUtils.getCurPC().isEmpty());

        PathUtils.reset();
        PathUtils.recordBranchConstraint(tag, 158, true); // IFLE
        assertFalse(PathUtils.getCurPC().isEmpty());
    }
}
