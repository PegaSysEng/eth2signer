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
package tech.pegasys.web3signer.slashingprotection;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;

import com.google.common.io.Resources;
import com.opentable.db.postgres.embedded.EmbeddedPostgres;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InterchangeImportBadJsonFormattingIntegrationTest
    extends InterchangeBaseIntegrationTest {

  private EmbeddedPostgres db;
  private String databaseUrl;
  private Jdbi jdbi;
  private SlashingProtection slashingProtection;

  private final String USERNAME = "postgres";
  private final String PASSWORD = "postgres";

  @BeforeEach
  public void setupTest() {
    try {
      db = setup();
      databaseUrl =
          String.format("jdbc:postgresql://localhost:%d/postgres", db.getPort());
      jdbi = DbConnection.createConnection(databaseUrl, USERNAME, PASSWORD);
      slashingProtection =
          SlashingProtectionFactory.createSlashingProtection(databaseUrl, USERNAME, PASSWORD);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @AfterEach()
  public void cleanup() {
    if (db != null) {
      try {
        db.close();
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
      db = null;
    }
  }


  @Test
  void incorrectlyTypedDataFieldThrowsException() {
    final URL importFile = Resources.getResource("interchange/dataFieldNotArray.json");
    assertThatThrownBy(() -> slashingProtection.importData(importFile.openStream()))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to import database content");
    assertDbIsEmpty(jdbi);
  }

  @Test
  void missingDataSectionInImportResultsInAnEmptyDatabase() throws IOException {
    final URL importFile = Resources.getResource("interchange/missingDataField.json");
    slashingProtection.importData(importFile.openStream());
    assertDbIsEmpty(jdbi);
  }

  @Test
  void emptyDataSectionInImportResultsInAnEmptyDatabase() throws IOException {
    final URL importFile = Resources.getResource("interchange/emptyDataArray.json");
    slashingProtection.importData(importFile.openStream());
    assertDbIsEmpty(jdbi);
  }

  @Test
  void anErrorInSubsequentBlockRollsbackToAnEmptyDatabase() {
      final URL importFile = Resources.getResource("interchange/errorInSecondBlock.json");
      assertThatThrownBy(() -> slashingProtection.importData(importFile.openStream()))
          .isInstanceOf(RuntimeException.class)
          .hasMessage(("Failed to import database content"));
      assertDbIsEmpty(jdbi);
  }

  @Test
  void missingPublicKeyFieldThrowsExceptionAndLeavesDbEmpty() {
      final URL importFile = Resources.getResource("interchange/missingPublicKey.json");
      assertThatThrownBy(() -> slashingProtection.importData(importFile.openStream()))
          .isInstanceOf(RuntimeException.class)
          .hasMessage(("Failed to import database content"));
      assertDbIsEmpty(jdbi);
  }
}
