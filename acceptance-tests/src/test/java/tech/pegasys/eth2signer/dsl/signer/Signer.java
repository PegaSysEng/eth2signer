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
package tech.pegasys.eth2signer.dsl.signer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.eth2signer.dsl.utils.WaitUtils.waitFor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Signer {

  private static final Logger LOG = LogManager.getLogger();
  private static final String PROCESS_NAME = "Eth2Signer";

  private final Eth2SignerProcessRunner runner;
  private final String hostname;
  private final String urlFormatting;
  private final Vertx vertx;
  private HttpClient httpClient;

  public Signer(final SignerConfiguration signerConfig) {
    this.runner = new Eth2SignerProcessRunner(signerConfig);
    this.hostname = signerConfig.hostname();
    urlFormatting = "http://%s:%s";
    vertx = Vertx.vertx();
  }

  public void start() {
    LOG.info("Starting Eth2Signer");
    runner.start(PROCESS_NAME);
    final String httpJsonRpcUrl = getUrl();
    LOG.info("Http requests being submitted to : {} ", httpJsonRpcUrl);

    HttpClientOptions options = new HttpClientOptions();
    options.setDefaultHost(this.hostname);
    options.setDefaultPort(runner.httpJsonRpcPort());
    httpClient = vertx.createHttpClient();
  }

  public void shutdown() {
    LOG.info("Shutting down Eth2Signer");
    vertx.close();
    runner.shutdown();
  }

  public boolean isRunning() {
    return runner.isRunning(PROCESS_NAME);
  }

  public boolean isListening() {
    final CompletableFuture<String> responseBodyFuture = new CompletableFuture<>();
    final HttpClientRequest request =
        httpClient.get(
            "/upcheck",
            response -> {
              if (response.statusCode() == HttpResponseStatus.OK.code()) {
                response.bodyHandler(body -> responseBodyFuture.complete(body.toString(UTF_8)));
              }
              responseBodyFuture.completeExceptionally(new RuntimeException("Illegal response"));
            });
    request.setChunked(false);
    request.end();

    final String body;
    try {
      body = responseBodyFuture.get();
    } catch (final ExecutionException e) {
      throw (RuntimeException) e.getCause();
    } catch (final InterruptedException e) {
      throw new RuntimeException("Thread was interrupted waiting for Eth2Signer response.");
    }
    return body.equals("OK");
  }

  public void awaitStartupCompletion() {
    LOG.info("Waiting for Signer to become responsive...");
    final int secondsToWait = Boolean.getBoolean("debugSubProcess") ? 3600 : 30;
    waitFor(secondsToWait, () -> assertThat(isListening()).isTrue());
    LOG.info("Signer is now responsive");
  }

  public String getUrl() {
    return String.format(urlFormatting, hostname, runner.httpJsonRpcPort());
  }
}