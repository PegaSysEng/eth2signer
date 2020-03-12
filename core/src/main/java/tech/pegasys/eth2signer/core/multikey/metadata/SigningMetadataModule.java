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

import tech.pegasys.eth2signer.crypto.SecretKey;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.tuweni.bytes.Bytes;

public class SigningMetadataModule extends SimpleModule {

  public SigningMetadataModule() {
    super("SigningMetadata");
    addDeserializer(SecretKey.class, new PrivateKeyDeserializer());
  }

  private static class PrivateKeyDeserializer extends JsonDeserializer<SecretKey> {

    @Override
    public SecretKey deserialize(JsonParser p, DeserializationContext ctxt) {
      try {
        final Bytes privateKeyBytes = Bytes.fromHexString(p.getValueAsString());
        return SecretKey.fromBytes(privateKeyBytes);
      } catch (Exception e) {
        throw new SigningMetadataException("Invalid hex value for private key", e);
      }
    }
  }
}
