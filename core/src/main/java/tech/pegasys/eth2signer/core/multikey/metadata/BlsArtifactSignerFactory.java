/*
 * Copyright 2020 ConsenSys AG.
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
package tech.pegasys.eth2signer.core.multikey.metadata;

import tech.pegasys.eth2signer.core.metrics.Eth2SignerMetricCategory;
import tech.pegasys.eth2signer.core.multikey.metadata.model.AzureSecretSigningMetadata;
import tech.pegasys.eth2signer.core.multikey.metadata.model.FileKeyStoreMetadata;
import tech.pegasys.eth2signer.core.multikey.metadata.model.FileRawSigningMetadata;
import tech.pegasys.eth2signer.core.multikey.metadata.model.HashicorpSigningMetadata;
import tech.pegasys.eth2signer.core.signing.ArtifactSigner;
import tech.pegasys.eth2signer.core.signing.BlsArtifactSigner;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.KeyStoreValidationException;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.signers.hashicorp.HashicorpConnectionFactory;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSecretKey;

import java.nio.file.Path;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer.TimingContext;

public class BlsArtifactSignerFactory extends AbstractArtifactSignerFactory {

  private final LabelledMetric<OperationTimer> privateKeyRetrievalTimer;

  public BlsArtifactSignerFactory(
      final Path configsDirectory,
      final MetricsSystem metricsSystem,
      final HashicorpConnectionFactory connectionFactory) {
    super(connectionFactory, configsDirectory);
    privateKeyRetrievalTimer =
        metricsSystem.createLabelledTimer(
            Eth2SignerMetricCategory.SIGNING,
            "private_key_retrieval_time",
            "Time taken to retrieve private key",
            "signer");
  }

  @Override
  public ArtifactSigner create(final FileRawSigningMetadata fileRawSigningMetadata) {
    try (TimingContext ignored = privateKeyRetrievalTimer.labels("file-raw").startTimer()) {
      return new BlsArtifactSigner(
          new BLSKeyPair(BLSSecretKey.fromBytes(fileRawSigningMetadata.getPrivateKeyBytes())));
    }
  }

  @Override
  public ArtifactSigner create(final FileKeyStoreMetadata fileKeyStoreMetadata) {
    try (TimingContext ignored = privateKeyRetrievalTimer.labels("file-keystore").startTimer()) {
      return createKeystoreArtifact(fileKeyStoreMetadata);
    }
  }

  @Override
  public ArtifactSigner create(final HashicorpSigningMetadata hashicorpMetadata) {
    try (TimingContext ignored = privateKeyRetrievalTimer.labels("hashicorp").startTimer()) {
      final Bytes privateKeyBytes = extractBytesFromVault(hashicorpMetadata);
      final BLSKeyPair keyPair = new BLSKeyPair(BLSSecretKey.fromBytes(privateKeyBytes));
      return new BlsArtifactSigner(keyPair);
    }
  }

  @Override
  public ArtifactSigner create(final AzureSecretSigningMetadata azureSecretSigningMetadata) {
    try (TimingContext ignored = privateKeyRetrievalTimer.labels("azure").startTimer()) {
      final Bytes privateKeyBytes = extractBytesFromVault(azureSecretSigningMetadata);
      final BLSKeyPair keyPair = new BLSKeyPair(BLSSecretKey.fromBytes(privateKeyBytes));
      return new BlsArtifactSigner(keyPair);
    }
  }

  private ArtifactSigner createKeystoreArtifact(final FileKeyStoreMetadata fileKeyStoreMetadata) {
    final Path keystoreFile = makeRelativePathAbsolute(fileKeyStoreMetadata.getKeystoreFile());
    final Path keystorePasswordFile =
        makeRelativePathAbsolute(fileKeyStoreMetadata.getKeystorePasswordFile());
    try {
      final KeyStoreData keyStoreData = KeyStoreLoader.loadFromFile(keystoreFile);
      final String password = loadPassword(keystorePasswordFile);
      final Bytes privateKey = KeyStore.decrypt(password, keyStoreData);
      final BLSKeyPair keyPair = new BLSKeyPair(BLSSecretKey.fromBytes(privateKey));
      return new BlsArtifactSigner(keyPair);
    } catch (final KeyStoreValidationException e) {
      throw new SigningMetadataException(e.getMessage(), e);
    }
  }
}
