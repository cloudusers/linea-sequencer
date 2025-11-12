/*
 * Copyright Consensys Software Inc.
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
package net.consensys.linea.sequencer.txpoolvalidation.validators;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidator;

/**
 * Validator that checks if the sender or the recipient are accepted. By default, precompiles are
 * not valid recipient.
 */
@Slf4j
@RequiredArgsConstructor
public class AllowedAddressValidator implements PluginTransactionPoolValidator {
  // 基础预编译地址集
  private static final Set<Address> BASE_PRECOMPILES =
      Set.of(
          Address.fromHexString("0x0000000000000000000000000000000000000001"),
          Address.fromHexString("0x0000000000000000000000000000000000000002"),
          Address.fromHexString("0x0000000000000000000000000000000000000003"),
          Address.fromHexString("0x0000000000000000000000000000000000000004"),
          Address.fromHexString("0x0000000000000000000000000000000000000005"),
          Address.fromHexString("0x0000000000000000000000000000000000000006"),
          Address.fromHexString("0x0000000000000000000000000000000000000007"),
          Address.fromHexString("0x0000000000000000000000000000000000000008"),
          Address.fromHexString("0x0000000000000000000000000000000000000009"),
          Address.fromHexString("0x000000000000000000000000000000000000000a"));
  
  // 可配置的预编译地址集，允许通过配置扩展
  private final Set<Address> additionalPrecompiles;
  
  // 合并基础预编译和额外预编译地址
  private final Set<Address> precompiles;
  
  public AllowedAddressValidator(final Set<Address> denied) {
    this.denied = denied;
    // 默认无额外预编译地址
    this.additionalPrecompiles = Collections.emptySet();
    this.precompiles = Collections.unmodifiableSet(
        Stream.concat(BASE_PRECOMPILES.stream(), additionalPrecompiles.stream())
            .collect(Collectors.toSet()));
  }
  
  // 支持自定义预编译地址的构造函数
  public AllowedAddressValidator(final Set<Address> denied, final Set<Address> additionalPrecompiles) {
    this.denied = denied;
    this.additionalPrecompiles = additionalPrecompiles != null ? additionalPrecompiles : Collections.emptySet();
    this.precompiles = Collections.unmodifiableSet(
        Stream.concat(BASE_PRECOMPILES.stream(), this.additionalPrecompiles.stream())
            .collect(Collectors.toSet()));
    if (!this.additionalPrecompiles.isEmpty()) {
      log.info("Added {} additional precompile addresses to validator", this.additionalPrecompiles.size());
    }
  }

  private final Set<Address> denied;

  @Override
  public Optional<String> validateTransaction(
      final Transaction transaction, final boolean isLocal, final boolean hasPriority) {
    return validateSender(transaction).or(() -> validateRecipient(transaction));
  }

  private Optional<String> validateRecipient(final Transaction transaction) {
    if (transaction.getTo().isPresent()) {
      final Address to = transaction.getTo().get();
      if (denied.contains(to)) {
        final String errMsg =
            String.format(
                "recipient %s is blocked as appearing on the SDN or other legally prohibited list",
                to);
        log.debug(errMsg);
        return Optional.of(errMsg);
      } else if (precompiles.contains(to)) {
        final String errMsg =
            String.format("destination address %s is a precompile address and cannot receive transactions", to);
        log.debug(errMsg);
        return Optional.of(errMsg);
      }
    }
    return Optional.empty();
  }

  private Optional<String> validateSender(final Transaction transaction) {
    if (denied.contains(transaction.getSender())) {
      final String errMsg =
          String.format(
              "sender %s is blocked as appearing on the SDN or other legally prohibited list",
              transaction.getSender());
      log.debug(errMsg);
      return Optional.of(errMsg);
    }
    return Optional.empty();
  }
}
