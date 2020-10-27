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
package tech.pegasys.web3signer.core.multikey.metadata;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

import tech.pegasys.signers.azure.AzureKeyVault;
import tech.pegasys.signers.hashicorp.HashicorpConnection;
import tech.pegasys.signers.hashicorp.HashicorpConnectionFactory;
import tech.pegasys.signers.hashicorp.TrustStoreType;
import tech.pegasys.signers.hashicorp.config.ConnectionParameters;
import tech.pegasys.signers.hashicorp.config.KeyDefinition;
import tech.pegasys.signers.hashicorp.config.TlsOptions;
import tech.pegasys.signers.interlock.InterlockSession;
import tech.pegasys.signers.interlock.InterlockSessionFactoryProvider;
import tech.pegasys.signers.interlock.vertx.InterlockSessionFactoryImpl;
import tech.pegasys.web3signer.core.signing.KeyType;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import com.google.common.io.Files;
import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;

public abstract class AbstractArtifactSignerFactory implements ArtifactSignerFactory {

  final Vertx vertx;
  final HashicorpConnectionFactory hashicorpConnectionFactory;
  final Path configsDirectory;

  protected AbstractArtifactSignerFactory(final Vertx vertx, final Path configsDirectory) {
    this.vertx = vertx;
    this.hashicorpConnectionFactory = new HashicorpConnectionFactory(vertx);
    this.configsDirectory = configsDirectory;
  }

  protected Bytes extractBytesFromVault(final AzureSecretSigningMetadata metadata) {
    final AzureKeyVault azureVault =
        new AzureKeyVault(
            metadata.getClientId(),
            metadata.getClientSecret(),
            metadata.getTenantId(),
            metadata.getVaultName());

    return azureVault
        .fetchSecret(metadata.getSecretName())
        .map(Bytes::fromHexString)
        .orElseThrow(
            () ->
                new SigningMetadataException(
                    "secret '" + metadata.getSecretName() + "' doesn't exist"));
  }

  protected Bytes extractBytesFromVault(final HashicorpSigningMetadata metadata) {
    final Optional<TlsOptions> tlsOptions = buildTlsOptions(metadata);

    try {
      final HashicorpConnection connection =
          hashicorpConnectionFactory.create(
              new ConnectionParameters(
                  metadata.getServerHost(),
                  Optional.ofNullable(metadata.getServerPort()),
                  tlsOptions,
                  Optional.ofNullable(metadata.getTimeout())));

      final String secret =
          connection.fetchKey(
              new KeyDefinition(
                  metadata.getKeyPath(),
                  Optional.ofNullable(metadata.getKeyName()),
                  metadata.getToken()));
      return Bytes.fromHexString(secret);
    } catch (final Exception e) {
      throw new SigningMetadataException("Failed to fetch secret from hashicorp vault", e);
    }
  }

  protected Bytes extractBytesFromInterlock(final InterlockSigningMetadata metadata) {
    final InterlockSessionFactoryImpl interlockSessionFactory =
        InterlockSessionFactoryProvider.newInstance(vertx, metadata.getKnownServersFile());
    try (final InterlockSession session =
        interlockSessionFactory.newSession(
            metadata.getInterlockUrl(), metadata.getVolume(), metadata.getPassword())) {
      return session.fetchKey(metadata.getKeyPath());
    } catch (final RuntimeException e) {
      throw new SigningMetadataException(
          "Failed to fetch secret from Interlock: " + e.getMessage(), e);
    }
  }

  private Optional<TlsOptions> buildTlsOptions(final HashicorpSigningMetadata metadata) {
    if (metadata.getTlsEnabled()) {
      final Path knownServerFile = metadata.getTlsKnownServerFile();
      if (knownServerFile == null) {
        return Optional.of(new TlsOptions(Optional.empty(), null, null)); // use CA Auth
      } else {
        final Path configRelativeKnownServerPath = makeRelativePathAbsolute(knownServerFile);
        if (!configRelativeKnownServerPath.toFile().exists()) {
          throw new SigningMetadataException(
              String.format(
                  "Known servers file (%s) does not exist.", configRelativeKnownServerPath));
        }
        return Optional.of(
            new TlsOptions(Optional.of(TrustStoreType.WHITELIST), knownServerFile, null));
      }
    }
    return Optional.empty();
  }

  protected String loadPassword(final Path passwordFile) {
    try {
      final String password = Files.asCharSource(passwordFile.toFile(), UTF_8).readFirstLine();
      if (password == null || password.isBlank()) {
        throw new SigningMetadataException("Keystore password cannot be empty: " + passwordFile);
      }
      return password;
    } catch (final FileNotFoundException e) {
      throw new SigningMetadataException("Keystore password file not found: " + passwordFile, e);
    } catch (final IOException e) {
      final String errorMessage =
          format(
              "Unexpected IO error while reading keystore password file [%s]: %s",
              passwordFile, e.getMessage());
      throw new SigningMetadataException(errorMessage, e);
    }
  }

  protected Path makeRelativePathAbsolute(final Path path) {
    return path.isAbsolute() ? path : configsDirectory.resolve(path);
  }

  public abstract KeyType getKeyType();
}
