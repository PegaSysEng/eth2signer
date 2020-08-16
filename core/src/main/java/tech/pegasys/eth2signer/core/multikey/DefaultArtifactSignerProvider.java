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
package tech.pegasys.eth2signer.core.multikey;

import tech.pegasys.eth2signer.core.signing.ArtifactSigner;
import tech.pegasys.eth2signer.core.signing.ArtifactSignerProvider;
import tech.pegasys.eth2signer.core.signing.BlsArtifactSigner;
import tech.pegasys.eth2signer.core.signing.Curve;
import tech.pegasys.eth2signer.core.signing.SecpArtifactSigner;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DefaultArtifactSignerProvider implements ArtifactSignerProvider {

  private static final Logger LOG = LogManager.getLogger();
  private final Map<String, ArtifactSigner> signers;

  private DefaultArtifactSignerProvider(final Map<String, ArtifactSigner> signers) {
    this.signers = signers;
  }

  public static DefaultArtifactSignerProvider create(final Collection<ArtifactSigner> signers) {
    final Map<String, ArtifactSigner> signerMap =
        signers
            .parallelStream()
            .collect(
                Collectors.toMap(
                    signer -> normaliseIdentifier(signer.getIdentifier()),
                    Function.identity(),
                    (signer1, signer2) -> {
                      LOG.warn("Duplicate keys were found.");
                      return signer1;
                    }));
    return new DefaultArtifactSignerProvider(signerMap);
  }

  @Override
  public Optional<ArtifactSigner> getSigner(final Curve curve, final String identifier) {
    final String normalisedIdentifier = normaliseIdentifier(identifier);
    final Optional<ArtifactSigner> result = Optional.ofNullable(signers.get(normalisedIdentifier));

    if (result.isEmpty()) {
      LOG.error("No signer was loaded matching identifier '{}'", identifier);
      return Optional.empty();
    } else {
      if ((result.get() instanceof BlsArtifactSigner) && curve.equals(Curve.BLS)) {
        return result;
      } else if ((result.get() instanceof SecpArtifactSigner) && curve.equals(Curve.SECP256K1)) {
        return result;
      } else {
        LOG.error("Requested signers does not conform to expected curve ({})", curve.asString());
        return Optional.empty();
      }
    }
  }

  @Override
  public Set<String> availableIdentifiers() {
    return signers.keySet().parallelStream().map(id -> "0x" + id).collect(Collectors.toSet());
  }

  private static String normaliseIdentifier(final String signerIdentifier) {
    final String lowerCaseIdentifier = signerIdentifier.toLowerCase();
    return lowerCaseIdentifier.startsWith("0x")
        ? lowerCaseIdentifier.substring(2)
        : lowerCaseIdentifier;
  }
}