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
package tech.pegasys.eth2signer.core.signers.unencryptedfile;

import com.google.common.base.Charsets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.eth2signer.core.signing.ArtifactSigner;
import tech.pegasys.eth2signer.crypto.KeyPair;
import tech.pegasys.eth2signer.crypto.SecretKey;

public class UnencryptedKeyFileSignerFactory {

  public static ArtifactSigner createSigner(final Path keyFilePath) throws IOException {
    final byte[] fileContent = Files.readAllBytes(keyFilePath);
    final String keyString = new String(fileContent, Charsets.UTF_8);
    final KeyPair keys = new KeyPair(SecretKey.fromBytes(Bytes.fromHexString(keyString)));

    return new ArtifactSigner(keys);
  }
}