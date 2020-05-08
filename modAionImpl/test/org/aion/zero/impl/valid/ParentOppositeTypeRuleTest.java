package org.aion.zero.impl.valid;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.mcf.blockchain.BlockHeader.Seal.PROOF_OF_STAKE;
import static org.aion.mcf.blockchain.BlockHeader.Seal.PROOF_OF_WORK;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;
import org.aion.mcf.blockchain.BlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link BlockNumberRule}.
 *
 * @author Alexandra Roatis
 */
public class ParentOppositeTypeRuleTest {

    @Mock BlockHeader mockChildBH;
    @Mock BlockHeader mockParentBH;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Checks if the {@link ParentOppositeTypeRule#validate} returns {@code true} when the block type is opposite
     * to the parent's type.
     */
    @Test
    public void testValid() {
        // define return value for method getSealType()
        when(mockChildBH.getSealType()).thenReturn(PROOF_OF_WORK);
        when(mockParentBH.getSealType()).thenReturn(PROOF_OF_STAKE);
        List<RuleError> errors = new LinkedList<>();

        // generate output
        boolean actual = new ParentOppositeTypeRule().validate(mockChildBH, mockParentBH, errors);

        // test output
        assertThat(actual).isTrue();
    }

    /**
     * Checks if the {@link ParentOppositeTypeRule#validate} returns {@code false} when the block type is the same
     * as the parent's type.
     */
    @Test
    public void testInvalid() {
        // define return value for method getSealType()
        when(mockChildBH.getSealType()).thenReturn(PROOF_OF_STAKE);
        when(mockParentBH.getSealType()).thenReturn(PROOF_OF_STAKE);
        List<RuleError> errors = new LinkedList<>();

        // generate output
        boolean actual = new ParentOppositeTypeRule().validate(mockChildBH, mockParentBH, errors);

        // test output
        assertThat(actual).isFalse();

        // redefine return value for method getSealType()
        when(mockChildBH.getSealType()).thenReturn(PROOF_OF_WORK);
        when(mockParentBH.getSealType()).thenReturn(PROOF_OF_WORK);
        

        // regenerate output
        actual = new ParentOppositeTypeRule().validate(mockChildBH, mockParentBH, errors);

        // test output
        assertThat(actual).isFalse();
    }
}
