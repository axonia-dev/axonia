/**
 * The data-subject-rights bounded context: orchestrates a self-serve member's account erasure
 * ({@link dev.axonia.privacy.AccountErasure}), guarding it against enterprise membership and sole hub ownership
 * ({@link dev.axonia.privacy.ErasurePreflight}, {@link dev.axonia.privacy.ErasureBlocker}). Per Organization, the
 * database half runs as one transaction ({@link dev.axonia.privacy.internal.MemberPurge}), minting the unlinkable
 * surrogate an erasure leaves behind ({@link dev.axonia.privacy.internal.SurrogateIds}) and recording the anonymous
 * proof that it ran ({@code erasure_receipt}, via {@link dev.axonia.privacy.internal.ErasureReceiptRepository}). The
 * erasure and export ports themselves ({@link dev.axonia.MemberDataEraser}, {@link dev.axonia.PersonalDataFootprint})
 * live in the shared kernel because every module implements them; this module is where the orchestration and the
 * cross-module receipt live instead.
 */
@ApplicationModule(displayName = "Privacy")
package dev.axonia.privacy;

import org.springframework.modulith.ApplicationModule;
