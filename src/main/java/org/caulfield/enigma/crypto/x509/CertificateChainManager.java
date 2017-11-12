/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.caulfield.enigma.crypto.x509;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.caulfield.enigma.crypto.CryptoGenerator;
import org.caulfield.enigma.crypto.EnigmaException;
import org.caulfield.enigma.crypto.hash.HashCalculator;
import org.caulfield.enigma.database.CryptoDAO;
import org.caulfield.enigma.database.HSQLLoader;
import org.caulfield.enigma.stream.StreamManager;

/**
 *
 * @author Ender
 */
public class CertificateChainManager {

    public Iterable<String> getFullACList() {
        return new ArrayList<String>();
    }

    public String matchKeysAndCerts() {
        return "";
    }

    public long buildIntermediateCertificate(Integer idParentCert, String subject, String caPKPassword) {
        InputStream caCertIS = CryptoDAO.getCertFromDB(idParentCert);
        CryptoGenerator cg = new CryptoGenerator();
        X509Certificate caCert = cg.getCertificate(caCertIS);
        HSQLLoader sql = new HSQLLoader();
        long certID = 0;
        try {
            X509CertificateHolder caCertHolder = new JcaX509CertificateHolder(caCert);

            ResultSet set = sql.runQuery("select ALGO,ID_PRIVATEKEY, CERTNAME from CERTIFICATES WHERE ID_CERT=" + idParentCert);
            if (set.next()) {
                String algo = set.getString("ALGO");
                String certName = set.getString("CERTNAME");
                Integer associatedPK = set.getInt("ID_PRIVATEKEY");
                InputStream caPKIS = CryptoDAO.getKeyFromDB(associatedPK);
                PrivateKey caPK = cg.getPrivateKey(caPKIS, caPKPassword);
                String pkAlgo = caPK.getAlgorithm();
                AsymmetricCipherKeyPair kp = CryptoGenerator.createKeyPair(pkAlgo);
                PrivateKeyInfo privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(kp.getPrivate());
                PrivateKey intermediatePK = new JcaPEMKeyConverter().getPrivateKey(privateKeyInfo);
                SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(kp.getPublic());
                PublicKey intermediatePubK = new JcaPEMKeyConverter().getPublicKey(publicKeyInfo);
                X509CertificateHolder cert = PKCS12Builder.createIntermediateCert(intermediatePubK, caPK, caCertHolder, subject, algo);
                HashCalculator hc = new HashCalculator();
                InputStream intermediatePKStream = StreamManager.convertPrivateKeyToInputStream(intermediatePK, null);
                InputStream intermediatePubKStream = StreamManager.convertPublicKeyToInputStream(intermediatePubK);
                InputStream intermediatePKStream2 = StreamManager.convertPrivateKeyToInputStream(intermediatePK, null);
                InputStream intermediatePubKStream2 = StreamManager.convertPublicKeyToInputStream(intermediatePubK);
                long privKeyID = CryptoDAO.insertKeyInDB(intermediatePKStream, "SUB_" + certName + "_private", pkAlgo, hc.getStringChecksum(intermediatePKStream2, HashCalculator.SHA256), 0, true);
                long pubKeyID = CryptoDAO.insertKeyInDB(intermediatePubKStream, "SUB_" + certName + "_public", "Inherited", hc.getStringChecksum(intermediatePubKStream2, HashCalculator.SHA256), (int) (long) privKeyID, false);
                InputStream certStream = StreamManager.convertCertificateToInputStream(cert);
                InputStream certStream2 = StreamManager.convertCertificateToInputStream(cert);
                String thumbPrint = hc.getThumbprint(cert.getEncoded());
                certID = CryptoDAO.insertCertInDB(certStream, "SUB_" + certName, subject, hc.getStringChecksum(certStream2, HashCalculator.SHA256), algo, (int) (long) privKeyID, thumbPrint, idParentCert, 2,cert.getNotAfter());
            }

        } catch (SQLException ex) {
            Logger.getLogger(CertificateChainManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (CertificateEncodingException | EnigmaException ex) {
            Logger.getLogger(CertificateChainManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            Logger.getLogger(CertificateChainManager.class.getName()).log(Level.SEVERE, null, ex);
        }

        return certID;
    }

    public String buildUserCertificate(Integer idParentCert, String subject, String caPKPassword) {
        InputStream caCertIS = CryptoDAO.getCertFromDB(idParentCert);
        CryptoGenerator cg = new CryptoGenerator();
        X509Certificate caCert = cg.getCertificate(caCertIS);
        HSQLLoader sql = new HSQLLoader();

        try {
            X509CertificateHolder caCertHolder = new JcaX509CertificateHolder(caCert);

            ResultSet set = sql.runQuery("select ALGO,ID_PRIVATEKEY, CERTNAME from CERTIFICATES WHERE ID_CERT=" + idParentCert);
            if (set.next()) {
                String algo = set.getString("ALGO");
                String certName = set.getString("CERTNAME");
                Integer associatedPK = set.getInt("ID_PRIVATEKEY");
                InputStream caPKIS = CryptoDAO.getKeyFromDB(associatedPK);
                PrivateKey caPK = cg.getPrivateKey(caPKIS, caPKPassword);
                String pkAlgo = caPK.getAlgorithm();
                AsymmetricCipherKeyPair kp = CryptoGenerator.createKeyPair(pkAlgo);
                PrivateKeyInfo privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(kp.getPrivate());
                PrivateKey intermediatePK = new JcaPEMKeyConverter().getPrivateKey(privateKeyInfo);
                SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(kp.getPublic());
                PublicKey intermediatePubK = new JcaPEMKeyConverter().getPublicKey(publicKeyInfo);
                X509CertificateHolder cert = PKCS12Builder.createCert(intermediatePubK, caPK, caCert.getPublicKey(), caCertHolder, subject, algo);
                InputStream intermediatePKStream = StreamManager.convertPrivateKeyToInputStream(intermediatePK, null);
                InputStream intermediatePubKStream = StreamManager.convertPublicKeyToInputStream(intermediatePubK);
                InputStream intermediatePKStream2 = StreamManager.convertPrivateKeyToInputStream(intermediatePK, null);
                InputStream intermediatePubKStream2 = StreamManager.convertPublicKeyToInputStream(intermediatePubK);
                HashCalculator hc = new HashCalculator();
                long privKeyID = CryptoDAO.insertKeyInDB(intermediatePKStream, "USER_" + certName + "_private", pkAlgo, hc.getStringChecksum(intermediatePKStream2, HashCalculator.SHA256), 0, true);
                long pubKeyID = CryptoDAO.insertKeyInDB(intermediatePubKStream, "USER_" + certName + "_public", "Inherited", hc.getStringChecksum(intermediatePubKStream2, HashCalculator.SHA256), (int) (long) privKeyID, false);
                InputStream certStream = StreamManager.convertCertificateToInputStream(cert);
                InputStream certStream2 = StreamManager.convertCertificateToInputStream(cert);
                String thumbPrint = hc.getThumbprint(cert.getEncoded());
                long certID = CryptoDAO.insertCertInDB(certStream, "USER_" + certName, subject, hc.getStringChecksum(certStream2, HashCalculator.SHA256), algo, (int) (long) privKeyID, thumbPrint, idParentCert, 3,cert.getNotAfter());
                return "USER_" + certName + " created along with keys " + privKeyID + " and " + pubKeyID + ".";
            } else {
                return "CA Cert not found";
            }

        } catch (SQLException ex) {
            Logger.getLogger(CertificateChainManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (CertificateEncodingException | EnigmaException ex) {
            Logger.getLogger(CertificateChainManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            Logger.getLogger(CertificateChainManager.class.getName()).log(Level.SEVERE, null, ex);
        }

        return "";
    }
}
