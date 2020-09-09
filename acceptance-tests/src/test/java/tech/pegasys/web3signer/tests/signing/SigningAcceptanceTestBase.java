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
package tech.pegasys.web3signer.tests.signing;

import tech.pegasys.web3signer.dsl.signer.SignerConfigurationBuilder;
import tech.pegasys.web3signer.tests.AcceptanceTestBase;

import java.nio.file.Path;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.io.TempDir;

public class SigningAcceptanceTestBase extends AcceptanceTestBase {
  protected @TempDir Path testDirectory;

  protected void setupSigner(final String mode) {
    final SignerConfigurationBuilder builder = new SignerConfigurationBuilder();
    builder.withKeyStoreDirectory(testDirectory).withMode(mode);
    startSigner(builder.build());
  }

  protected Bytes verifyAndGetSignatureResponse(final Response response) {
    response.then().contentType(ContentType.TEXT).statusCode(200);
    return Bytes.fromHexString(response.body().print());
  }
}
