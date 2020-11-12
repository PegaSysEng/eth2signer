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
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.pegasys.web3signer.slashingprotection;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.Test;
import tech.pegasys.web3signer.slashingprotection.dao.SignedBlock;

public class InterchangeImportConflicts extends InterchangeBaseIntegrationTest {


  @Test
  void canImportFileWhichContainsConflictingBlockAndBothInDatabase() {

  }

  @Test
  void duplicateEntriesAreNotInsertedToDatabase() throws IOException {
    final URL importFile = Resources.getResource("interchange/singleValidBlock.json");
    slashingProtection.importData(importFile.openStream());
    slashingProtection.importData(importFile.openStream()); //attempt to reimport
    jdbi.useHandle(handle -> {
      final List<SignedBlock> blocksInDb = findAllBlocks(handle);
      assertThat(blocksInDb).hasSize(1);
      assertThat(blocksInDb.get(0).getSlot()).isEqualTo(UInt64.valueOf(12345));
      assertThat(blocksInDb.get(0).getValidatorId()).isEqualTo(UInt64.valueOf(1));
      assertThat(blocksInDb.get(0).getSigningRoot()).isEqualTo(Bytes
          .fromHexString("0x4ff6f743a43f3b4f95350831aeaf0a122a1a392922c45d804280284a69eb850b"));
    });
  }

  @Test
  void canloadConflictingBlocksInSameSlot() throws IOException {
    final URL importFile = Resources.getResource("interchange/conflictingBlocks.json");
    slashingProtection.importData(importFile.openStream());
    jdbi.useHandle(handle -> {
      final List<SignedBlock> blocksInDb = findAllBlocks(handle);
      assertThat(blocksInDb).hasSize(2);
      assertThat(blocksInDb.get(0).getSlot()).isEqualTo(UInt64.valueOf(12345));
      assertThat(blocksInDb.get(0).getValidatorId()).isEqualTo(1);
      assertThat(blocksInDb.get(0).getSigningRoot()).isEqualTo(Optional.of(Bytes
          .fromHexString("0x4ff6f743a43f3b4f95350831aeaf0a122a1a392922c45d804280284a69eb850b")));

      assertThat(blocksInDb.get(1).getSlot()).isEqualTo(UInt64.valueOf(12345));
      assertThat(blocksInDb.get(1).getValidatorId()).isEqualTo(1);
      assertThat(blocksInDb.get(1).getSigningRoot()).isEqualTo(Optional.of(Bytes
          .fromHexString("0x4ff6f743a43f3b4f95350831aeaf0a122a1a392922c45d804280284a69eb850c")));
    });
  }


}