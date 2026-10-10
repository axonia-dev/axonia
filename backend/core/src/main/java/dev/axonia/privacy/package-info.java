/**
 * The data-subject-rights bounded context: orchestrates a self-serve member's account erasure
 * ({@link dev.axonia.privacy.application.AccountErasure}), guarding it against enterprise membership and sole hub
 * ownership ({@link dev.axonia.privacy.application.ErasurePreflight},
 * {@link dev.axonia.privacy.application.ErasureBlocker}). Per Organization, the database half runs as one transaction
 * ({@link dev.axonia.privacy.application.MemberPurge}), minting the unlinkable surrogate an erasure leaves behind
 * ({@link dev.axonia.privacy.domain.SurrogateIds}) and recording the anonymous proof that it ran
 * ({@code erasure_receipt}, via {@link dev.axonia.privacy.application.port.out.ErasureReceipts}). The erasure and
 * export ports themselves ({@link dev.axonia.shared.api.personaldata.MemberDataEraser},
 * {@link dev.axonia.shared.api.personaldata.PersonalDataFootprint}) live in the shared kernel because every module
 * implements them; this module is where the orchestration and the cross-module receipt live instead.
 */
@ApplicationModule(displayName = "Privacy")
package dev.axonia.privacy;

import org.springframework.modulith.ApplicationModule;
