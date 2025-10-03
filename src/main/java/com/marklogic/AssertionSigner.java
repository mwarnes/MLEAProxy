package com.serverless.SAML;

import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.impl.AssertionMarshaller;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.security.credential.Credential;
import org.opensaml.xml.signature.Signature;
import org.opensaml.xml.signature.SignatureConstants;
import org.opensaml.xml.signature.SignatureException;
import org.opensaml.xml.signature.Signer;
import org.opensaml.xml.signature.impl.SignatureBuilder;

final class AssertionSigner {
    private final Credential signingCredential;

    static AssertionSigner createWithCredential(Credential signingCredential) {
        return new AssertionSigner(signingCredential);
    }

    private AssertionSigner(Credential signingCredential) {
        this.signingCredential = signingCredential;
    }

    Assertion signAssertion(Assertion assertion) throws MarshallingException, SignatureException {
        SignatureBuilder builder = new SignatureBuilder();
        Signature signature = builder.buildObject();

        signature.setSigningCredential(signingCredential);
        signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
        signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

        assertion.setSignature(signature);

        addXmlSignatureInstanceToAssertion(assertion);
        Signer.signObject(signature);

        return assertion;
    }

    private void addXmlSignatureInstanceToAssertion(Assertion assertion) throws MarshallingException {
        AssertionMarshaller marshaller = new AssertionMarshaller();
        marshaller.marshall(assertion);
    }
}
