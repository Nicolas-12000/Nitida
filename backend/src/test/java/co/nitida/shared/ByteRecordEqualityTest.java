package co.nitida.shared;

import static org.assertj.core.api.Assertions.assertThat;

import co.nitida.files.application.ReadFileUseCase;
import co.nitida.files.application.UploadFileUseCase;
import co.nitida.shared.crypto.KeyCipher;
import co.nitida.workshops.WorkshopsApi;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Sonar flags default record equality on byte[] fields; these helpers are production code and need Jacoco hits. */
class ByteRecordEqualityTest {
  @Test
  void keyCipherSealedComparesBytesByValue() {
    var a = new byte[] {1, 2};
    var one = new KeyCipher.Sealed(a, new byte[] {9});
    var same = new KeyCipher.Sealed(a.clone(), new byte[] {9});
    assertThat(one).isEqualTo(same);
    assertThat(one.hashCode()).isEqualTo(same.hashCode());
    assertThat(one.toString()).contains("Sealed");
    assertThat(one).isNotEqualTo("x");
    assertThat(new KeyCipher.Sealed(new byte[] {3}, new byte[] {9})).isNotEqualTo(one);
    assertThat(new KeyCipher.Sealed(a, new byte[] {8})).isNotEqualTo(one);
  }

  @Test
  void fileRecordsComparePayloadBytes() {
    var content = new byte[] {5, 6, 7};
    var result = new ReadFileUseCase.Result(content, "image/png");
    assertThat(new ReadFileUseCase.Result(content.clone(), "image/png")).isEqualTo(result);
    assertThat(result.hashCode()).isEqualTo(new ReadFileUseCase.Result(content.clone(), "image/png").hashCode());
    assertThat(result.toString()).contains("image/png");
    assertThat(new ReadFileUseCase.Result(new byte[] {1}, "image/png")).isNotEqualTo(result);
    assertThat(new ReadFileUseCase.Result(content, "text/plain")).isNotEqualTo(result);

    UUID workshop = UUID.randomUUID();
    var ctx = new WorkshopContext(workshop, workshop, Set.of(Role.WORKSHOP_OWNER));
    var command = new UploadFileUseCase.Command(ctx, content);
    assertThat(new UploadFileUseCase.Command(ctx, content.clone())).isEqualTo(command);
    assertThat(command.hashCode()).isEqualTo(new UploadFileUseCase.Command(ctx, content.clone()).hashCode());
    assertThat(command.toString()).contains("Command");
    assertThat(new UploadFileUseCase.Command(ctx, new byte[] {0})).isNotEqualTo(command);
    assertThat(new UploadFileUseCase.Command(new WorkshopContext(UUID.randomUUID(), workshop, Set.of(Role.WORKSHOP_OWNER)), content))
        .isNotEqualTo(command);
  }

  @Test
  void workshopApiRecordsCompareKeyMaterialByValue() {
    var keyId = UUID.randomUUID();
    var sig = new byte[] {11, 12};
    var signed = new WorkshopsApi.SignedHash(keyId, sig);
    assertThat(new WorkshopsApi.SignedHash(keyId, sig.clone())).isEqualTo(signed);
    assertThat(signed.hashCode()).isEqualTo(new WorkshopsApi.SignedHash(keyId, sig.clone()).hashCode());
    assertThat(signed.toString()).contains("SignedHash");
    assertThat(new WorkshopsApi.SignedHash(UUID.randomUUID(), sig)).isNotEqualTo(signed);
    assertThat(new WorkshopsApi.SignedHash(keyId, new byte[] {1})).isNotEqualTo(signed);

    var wsId = UUID.randomUUID();
    var pub = new byte[] {21, 22, 23};
    var info = new WorkshopsApi.PublicKeyInfo(keyId, wsId, "Ed25519", pub, false);
    assertThat(new WorkshopsApi.PublicKeyInfo(keyId, wsId, "Ed25519", pub.clone(), false)).isEqualTo(info);
    assertThat(info.hashCode()).isEqualTo(new WorkshopsApi.PublicKeyInfo(keyId, wsId, "Ed25519", pub.clone(), false).hashCode());
    assertThat(info.toString()).contains("PublicKeyInfo");
    assertThat(new WorkshopsApi.PublicKeyInfo(keyId, wsId, "Ed25519", new byte[] {1}, false)).isNotEqualTo(info);
    assertThat(new WorkshopsApi.PublicKeyInfo(keyId, wsId, "RSA", pub, false)).isNotEqualTo(info);
    assertThat(new WorkshopsApi.PublicKeyInfo(keyId, wsId, "Ed25519", pub, true)).isNotEqualTo(info);
  }
}