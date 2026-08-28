package ru.tyomakr.akcp.ingestion.web.service;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class UrlSafetyPolicy {
  private final HostResolver hostResolver;

  public UrlSafetyPolicy() {
    this(InetAddress::getAllByName);
  }

  UrlSafetyPolicy(HostResolver hostResolver) {
    this.hostResolver = hostResolver;
  }

  List<? extends SocketAddress> requirePublicSocketAddresses(
      List<? extends SocketAddress> addresses
  ) {
    if (addresses == null || addresses.isEmpty()) {
      throw new IllegalArgumentException("URL host did not resolve to an address");
    }
    for (SocketAddress socketAddress : addresses) {
      if (!(socketAddress instanceof InetSocketAddress inetSocketAddress)
          || inetSocketAddress.isUnresolved()
          || isBlocked(inetSocketAddress.getAddress())) {
        throw new IllegalArgumentException("URL connection resolved to a non-public address");
      }
    }
    return addresses;
  }

  public Mono<URI> validate(String value) {
    return Mono.defer(() -> {
      URI uri = parseUri(value);
      return Mono.fromCallable(() -> {
          InetAddress[] addresses = hostResolver.resolve(uri.getHost());
          if (addresses == null || addresses.length == 0) {
            throw new IllegalArgumentException("URL host did not resolve to an address");
          }
          if (Arrays.stream(addresses).anyMatch(this::isBlocked)) {
            throw new IllegalArgumentException("URL host resolves to a non-public address");
          }
          return uri;
        })
        .onErrorMap(UnknownHostException.class,
            error -> new IllegalArgumentException("URL host could not be resolved", error))
        .subscribeOn(Schedulers.boundedElastic());
    });
  }

  private URI parseUri(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("URL is required");
    }
    URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid URL", ex);
    }
    String scheme = Objects.toString(uri.getScheme(), "").toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw new IllegalArgumentException("Only http/https URLs are supported");
    }
    if (!uri.isAbsolute() || uri.getHost() == null || uri.getHost().isBlank()) {
      throw new IllegalArgumentException("URL must include a valid host");
    }
    if (uri.getRawUserInfo() != null) {
      throw new IllegalArgumentException("URL user info is not supported");
    }
    return uri;
  }

  private boolean isBlocked(InetAddress address) {
    if (address == null
        || address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    byte[] bytes = address.getAddress();
    if (address instanceof Inet4Address) {
      int first = Byte.toUnsignedInt(bytes[0]);
      int second = Byte.toUnsignedInt(bytes[1]);
      int third = Byte.toUnsignedInt(bytes[2]);
      return first == 0
          || (first == 100 && second >= 64 && second <= 127)
          || (first == 192 && (
              (second == 0 && (third == 0 || third == 2))
                  || (second == 88 && third == 99)
                  || second == 168))
          || (first == 198 && (second == 18
              || second == 19
              || (second == 51 && third == 100)))
          || (first == 203 && second == 0 && third == 113)
          || first >= 224;
    }
    if (address instanceof Inet6Address) {
      if (hasPrefix(bytes,
          new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff}, 96)) {
        try {
          return isBlocked(InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16)));
        } catch (UnknownHostException ex) {
          return true;
        }
      }
      int first = Byte.toUnsignedInt(bytes[0]);
      return (first & 0xfe) == 0xfc
          || hasPrefix(bytes, new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 96)
          || hasPrefix(bytes,
              new byte[]{0, 100, (byte) 0xff, (byte) 0x9b, 0, 0, 0, 0, 0, 0, 0, 0},
              96)
          || hasPrefix(bytes, new byte[]{0, 100, (byte) 0xff, (byte) 0x9b, 0, 1}, 48)
          || hasPrefix(bytes, new byte[]{1, 0, 0, 0, 0, 0, 0, 0}, 64)
          || hasPrefix(bytes, new byte[]{0x20, 0x01, 0}, 23)
          || hasPrefix(bytes, new byte[]{0x20, 0x01, 0x0d, (byte) 0xb8}, 32)
          || hasPrefix(bytes, new byte[]{0x20, 0x02}, 16);
    }
    return true;
  }

  private boolean hasPrefix(byte[] address, byte[] prefix, int prefixBits) {
    int fullBytes = prefixBits / 8;
    int remainingBits = prefixBits % 8;
    if (address.length * 8 < prefixBits
        || prefix.length < fullBytes + (remainingBits == 0 ? 0 : 1)) {
      return false;
    }
    for (int index = 0; index < fullBytes; index++) {
      if (address[index] != prefix[index]) {
        return false;
      }
    }
    if (remainingBits == 0) {
      return true;
    }
    int mask = 0xff << (8 - remainingBits);
    return (Byte.toUnsignedInt(address[fullBytes]) & mask)
        == (Byte.toUnsignedInt(prefix[fullBytes]) & mask);
  }

  @FunctionalInterface
  interface HostResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }
}
