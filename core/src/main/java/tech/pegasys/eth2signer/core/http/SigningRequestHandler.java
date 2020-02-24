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
package tech.pegasys.eth2signer.core.http;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.eth2signer.core.signing.ArtefactSignerProvider;
import tech.pegasys.eth2signer.core.signing.ArtifactSigner;
import tech.pegasys.eth2signer.core.utils.JsonDecoder;
import tech.pegasys.eth2signer.crypto.Signature;

public class SigningRequestHandler implements Handler<RoutingContext> {

  private static final Logger LOG = LogManager.getLogger();
  final ArtefactSignerProvider signerProvider;
  private final JsonDecoder jsonDecoder;

  public SigningRequestHandler(final ArtefactSignerProvider signerProvider,
      final JsonDecoder jsonDecoder) {
    this.signerProvider = signerProvider;
    this.jsonDecoder = jsonDecoder;
  }

  @Override
  public void handle(final RoutingContext context) {
    LOG.info("Received a request.");
    context.request().bodyHandler(body -> generateResponseFromBody(context.response(), body));
  }

  private void generateResponseFromBody(
      final HttpServerResponse response, final Buffer requestBody) {
    LOG.trace("Body received {}", requestBody.toString());
    final SigningRequestBody signingRequest =
        jsonDecoder.decodeValue(requestBody, SigningRequestBody.class);
    final Optional<ArtifactSigner> signer = signerProvider.getSigner(signingRequest.publicKey());

    if (signer.isPresent()) {
      final Bytes dataToSign = signingRequest.message();
      final Bytes domain = signingRequest.domain();
      final Signature signature = signer.get().sign(dataToSign, domain);
      response.end(signature.toString());
    } else {
      LOG.error("Unable to find an appropriate signer for request.");
      response.setStatusCode(404);
      response.setChunked(false);
      response.end("No key exists for requested signing operation.");
    }
  }
}
