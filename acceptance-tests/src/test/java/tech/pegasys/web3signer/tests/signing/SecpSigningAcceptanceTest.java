/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.web3signer.tests.signing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.web3j.crypto.Sign.publicKeyFromPrivate;
import static org.web3j.crypto.Sign.signedMessageToKey;

import tech.pegasys.signers.hashicorp.dsl.HashicorpNode;
import tech.pegasys.web3signer.core.signing.KeyType;
import tech.pegasys.web3signer.dsl.HashicorpSigningParams;
import tech.pegasys.web3signer.dsl.utils.MetadataFileHelpers;

import java.io.File;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.SignatureException;
import java.util.Map;

import com.google.common.io.Resources;
import io.restassured.response.Response;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.junit.jupiter.api.io.TempDir;
import org.web3j.crypto.Sign.SignatureData;

public class SecpSigningAcceptanceTest extends SigningAcceptanceTestBase {

  private static final String clientId = System.getenv("AZURE_CLIENT_ID");
  private static final String clientSecret = System.getenv("AZURE_CLIENT_SECRET");
  private static final String keyVaultName = System.getenv("AZURE_KEY_VAULT_NAME");
  private static final String tenantId = System.getenv("AZURE_TENANT_ID");

  private static final Bytes DATA = Bytes.wrap("42".getBytes(UTF_8));
  private static final String PRIVATE_KEY =
      "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";
  public static final String PUBLIC_KEY_HEX_STRING =
      "09b02f8a5fddd222ade4ea4528faefc399623af3f736be3c44f03e2df22fb792f3931a4d9573d333ca74343305762a753388c3422a86d98b713fc91c1ea04842";

  private final MetadataFileHelpers metadataFileHelpers = new MetadataFileHelpers();

  @Test
  @EnabledIfEnvironmentVariables({
    @EnabledIfEnvironmentVariable(named = "AZURE_CLIENT_ID", matches = ".*"),
    @EnabledIfEnvironmentVariable(named = "AZURE_CLIENT_SECRET", matches = ".*"),
    @EnabledIfEnvironmentVariable(named = "AZURE_KEY_VAULT_NAME", matches = ".*"),
    @EnabledIfEnvironmentVariable(named = "AZURE_KEY_TENANT_ID", matches = ".*")
  })
  public void signDataWithKeyInAzure(@TempDir Path keyConfigDirectory) {

    metadataFileHelpers.createAzureKeyYamlFileAt(
        keyConfigDirectory.resolve(PUBLIC_KEY_HEX_STRING + ".yaml"),
        clientId,
        clientSecret,
        keyVaultName,
        tenantId);

    signAndVerifySignature();
  }

  @Test
  public void signDataWithFileBasedKey(@TempDir Path keyConfigDirectory) throws URISyntaxException {
    final String keyPath =
        new File(Resources.getResource("secp256k1/wallet.json").toURI()).getAbsolutePath();

    metadataFileHelpers.createKeyStoreYamlFileAt(
        keyConfigDirectory.resolve(PUBLIC_KEY_HEX_STRING + ".yaml"),
        Path.of(keyPath),
        "pass",
        KeyType.SECP256K1);

    signAndVerifySignature();
  }

  @Test
  public void signDataWithKeyFromHashicorp(@TempDir Path keyConfigDirectory) {
    final HashicorpNode hashicorpNode = HashicorpNode.createAndStartHashicorp(true);
    try {
      final String secretPath = "acceptanceTestSecretPath";
      final String secretName = "secretName";
      hashicorpNode.addSecretsToVault(singletonMap(secretName, PRIVATE_KEY), secretPath);

      final HashicorpSigningParams hashicorpSigningParams =
          new HashicorpSigningParams(hashicorpNode, secretPath, secretName, KeyType.SECP256K1);

      metadataFileHelpers.createHashicorpYamlFileAt(
          keyConfigDirectory.resolve(PUBLIC_KEY_HEX_STRING + ".yaml"), hashicorpSigningParams);

      signAndVerifySignature();
    } finally {
      hashicorpNode.shutdown();
    }
  }

  @Test
  @EnabledIfEnvironmentVariables({
    @EnabledIfEnvironmentVariable(named = "AZURE_CLIENT_ID", matches = ".*"),
    @EnabledIfEnvironmentVariable(named = "AZURE_CLIENT_SECRET", matches = ".*"),
    @EnabledIfEnvironmentVariable(named = "AZURE_KEY_VAULT_NAME", matches = ".*"),
    @EnabledIfEnvironmentVariable(named = "AZURE_TENANT_ID", matches = ".*")
  })
  public void signDatWithKeyFromAzure(@TempDir Path keyConfigDirectory) {
    metadataFileHelpers.createAzureKeyYamlFileAt(
        keyConfigDirectory.resolve(PUBLIC_KEY_HEX_STRING + ".yaml"),
        clientId,
        clientSecret,
        keyVaultName,
        tenantId);

    signAndVerifySignature();
  }

  private void signAndVerifySignature() {
    signAndVerifySignature(null);
  }

  @Test
  @Disabled("Requires access to Interlock on Armory II")
  public void secpSingingUsingInterlock() {
    final Path configFile = testDirectory.resolve("interlock_2.yaml");
    final Path knownServersFile = testDirectory.resolve("interlockKnownServer.txt");

    metadataFileHelpers.createInterlockYamlFileAt(
        configFile, knownServersFile, Path.of("/secp/key1.txt"), KeyType.SECP256K1);

    signAndVerifySignature();
  }

  private void signAndVerifySignature(final Map<String, String> env) {
    setupSigner("eth1", env);

    // openapi
    final Response response = signer.eth1Sign(PUBLIC_KEY_HEX_STRING, DATA);
    final Bytes signature = verifyAndGetSignatureResponse(response);
    verifySignature(signature);
  }

  void verifySignature(final Bytes signature) {
    final BigInteger privateKey = new BigInteger(1, Bytes.fromHexString(PRIVATE_KEY).toArray());
    final BigInteger expectedPublicKey = publicKeyFromPrivate(privateKey);

    final byte[] r = signature.slice(0, 32).toArray();
    final byte[] s = signature.slice(32, 32).toArray();
    final byte[] v = signature.slice(64).toArray();
    final BigInteger messagePublicKey = recoverPublicKey(new SignatureData(v, r, s));
    assertThat(messagePublicKey).isEqualTo(expectedPublicKey);
  }

  private BigInteger recoverPublicKey(final SignatureData signature) {
    try {
      return signedMessageToKey(DATA.toArray(), signature);
    } catch (final SignatureException e) {
      throw new IllegalStateException("signature cannot be recovered", e);
    }
  }
}
