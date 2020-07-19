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
package tech.pegasys.eth2signer.core.http.handlers;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

import tech.pegasys.eth2signer.core.http.models.SigningRequestBody;
import tech.pegasys.eth2signer.core.signing.ArtifactSignature;
import tech.pegasys.eth2signer.core.signing.ArtifactSignatureType;
import tech.pegasys.eth2signer.core.signing.ArtifactSigner;
import tech.pegasys.eth2signer.core.signing.ArtifactSignerProvider;

import java.util.Optional;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameter;
import io.vertx.ext.web.api.RequestParameters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class SignForPublicKeyHandler<T extends ArtifactSignature>
    implements Handler<RoutingContext> {
  private static final Logger LOG = LogManager.getLogger();
  final ArtifactSignerProvider signerProvider;
  private final SignatureFormatter<T> signatureFormatter;
  private final ArtifactSignatureType type;

  public SignForPublicKeyHandler(
      final ArtifactSignerProvider signerProvider,
      SignatureFormatter<T> signatureFormatter,
      ArtifactSignatureType type) {
    this.signerProvider = signerProvider;
    this.signatureFormatter = signatureFormatter;
    this.type = type;
  }

  @Override
  public void handle(RoutingContext routingContext) {
    final RequestParameters params = routingContext.get("parsedParameters");
    final String publicKey = params.pathParameter("publicKey").toString();
    final Optional<ArtifactSigner> signer = signerProvider.getSigner(publicKey);
    if (signer.isEmpty()) {
      LOG.error("Unable to find an appropriate signer for request: {}", publicKey);
      routingContext.fail(404);
      return;
    }

    final Bytes dataToSign = getDataToSign(params);
    final ArtifactSignature artifactSignature = signer.get().sign(dataToSign);
    final String formatSignature = formatSignature(artifactSignature);

    routingContext
        .response()
        .putHeader(CONTENT_TYPE, PLAIN_TEXT_UTF_8.toString())
        .end(formatSignature);
  }

  @SuppressWarnings("unchecked")
  private String formatSignature(final ArtifactSignature signature) {
    if (signature.getType() == type) {
      final T blsArtifactSignature = (T) signature;
      return signatureFormatter.format(blsArtifactSignature);
    } else {
      throw new IllegalStateException("Invalid signature type");
    }
  }

  private Bytes getDataToSign(final RequestParameters params) {
    final RequestParameter body = params.body();
    final JsonObject jsonObject = body.getJsonObject();
    final SigningRequestBody signingRequestBody = jsonObject.mapTo(SigningRequestBody.class);
    return signingRequestBody.data();
  }
}
