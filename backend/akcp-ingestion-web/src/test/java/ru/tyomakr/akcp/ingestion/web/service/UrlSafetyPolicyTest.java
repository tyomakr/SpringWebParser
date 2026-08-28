package ru.tyomakr.akcp.ingestion.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.test.StepVerifier;

class UrlSafetyPolicyTest {
  @Test
  void allowsPublicAddress() {
    UrlSafetyPolicy policy = policyWith("93.184.216.34");

    StepVerifier.create(policy.validate("https://public.example/image"))
        .assertNext(uri -> assertThat(uri.toString()).isEqualTo("https://public.example/image"))
        .verifyComplete();
  }

  @Test
  void rejectsHostnameResolvingToPrivateAddress() {
    UrlSafetyPolicy policy = policyWith("10.20.30.40");

    StepVerifier.create(policy.validate("https://internal.example/secret"))
        .expectErrorSatisfies(error -> assertThat(error)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-public"))
        .verify();
  }

  @Test
  void rejectsHostnameWithMixedPublicAndPrivateAnswers() {
    UrlSafetyPolicy policy = new UrlSafetyPolicy(host -> new InetAddress[]{
        InetAddress.getByName("93.184.216.34"),
        InetAddress.getByName("127.0.0.1")
    });

    StepVerifier.create(policy.validate("https://mixed.example/"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void rejectsIpv6UniqueLocalAddress() {
    UrlSafetyPolicy policy = policyWith("fd00::1");

    StepVerifier.create(policy.validate("https://private-v6.example/"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void rejectsIpv4MappedLoopbackAddress() {
    UrlSafetyPolicy policy = policyWith("::ffff:127.0.0.1");

    StepVerifier.create(policy.validate("https://mapped-loopback.example/"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void rejectsPrivateAddressFromConnectTimeResolver() {
    UrlSafetyPolicy policy = policyWith("93.184.216.34");

    assertThatThrownBy(() -> policy.requirePublicSocketAddresses(
        java.util.List.of(
            new InetSocketAddress(InetAddress.getByName("93.184.216.34"), 443),
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 443))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("connection")
        .hasMessageContaining("non-public");
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "192.0.0.1",
      "192.0.2.1",
      "198.18.0.1",
      "198.51.100.1",
      "203.0.113.1",
      "64:ff9b::7f00:1",
      "64:ff9b:1::1",
      "100::1",
      "2001:db8::1",
      "2002:7f00:1::"
  })
  void rejectsSpecialUseAndIpv4TranslationAddresses(String address) {
    UrlSafetyPolicy policy = policyWith(address);

    StepVerifier.create(policy.validate("https://special.example/"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "192.3.1.1",
      "192.88.98.1",
      "64:ff9b:2::1"
  })
  void doesNotOverblockAddressesOutsideSpecialUsePrefixes(String address) {
    UrlSafetyPolicy policy = policyWith(address);

    StepVerifier.create(policy.validate("https://boundary.example/"))
        .expectNextCount(1)
        .verifyComplete();
  }

  @Test
  void rejectsMissingHostAndUserInfo() {
    UrlSafetyPolicy policy = policyWith("93.184.216.34");

    StepVerifier.create(policy.validate("https:///missing-host"))
        .expectError(IllegalArgumentException.class)
        .verify();
    StepVerifier.create(policy.validate("https://user@example.com/"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  private UrlSafetyPolicy policyWith(String address) {
    return new UrlSafetyPolicy(host -> new InetAddress[]{InetAddress.getByName(address)});
  }
}
