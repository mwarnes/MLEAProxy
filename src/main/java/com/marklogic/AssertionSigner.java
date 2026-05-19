package com.marklogic;

/**
 * SAML Assertion Signer - TEMPORARILY DISABLED
 * 
 * This class has been temporarily disabled due to OpenSAML dependency compatibility issues.
 * The OpenSAML library requires significant updates for Java 21 compatibility.
 * 
 * TODO: Update to OpenSAML 3.x or 4.x for proper Java 21 support
 * 
 * @deprecated This functionality is temporarily disabled pending OpenSAML upgrade
 */
@Deprecated
final class AssertionSigner {
    
    /**
     * Constructor disabled - OpenSAML functionality temporarily unavailable
     */
    private AssertionSigner() {
        throw new UnsupportedOperationException(
            "AssertionSigner is temporarily disabled due to OpenSAML compatibility issues with Java 21. " +
            "Please update OpenSAML dependencies to version 3.x or 4.x for Java 21 support."
        );
    }
    
    /**
     * Factory method disabled - OpenSAML functionality temporarily unavailable
     */
    static AssertionSigner createWithCredential(Object signingCredential) {
        throw new UnsupportedOperationException(
            "SAML assertion signing is temporarily disabled. " +
            "OpenSAML dependencies need to be updated for Java 21 compatibility."
        );
    }
}
