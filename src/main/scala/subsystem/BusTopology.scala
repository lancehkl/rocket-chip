// See LICENSE.SiFive for license details.

package freechips.rocketchip.subsystem

import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.Location

// These fields control parameters of the five traditional tilelink bus wrappers.
//   They continue to exist for backwards compatiblity reasons but could eventually be retired.

case object SystemBusKey extends Field[SystemBusParams]
case object FrontBusKey extends Field[FrontBusParams]
case object PeripheryBusKey extends Field[PeripheryBusParams]
case object ControlBusKey extends Field[PeripheryBusParams]
case object MemoryBusKey extends Field[MemoryBusParams]

// These objects serve as labels for specified attachment locations
//   from amongst the five traditional tilelink bus wrappers.
//   While they represent some tradtionally popular locations to attach devices,
//   there is no guarantee that they will exist in subsystems with
//   dynamically-configured topologies.

class TLBusWrapperLocation(name: String) extends Location[TLBusWrapper](name)
case object SBUS extends TLBusWrapperLocation("subsystem_sbus")
case object PBUS extends TLBusWrapperLocation("subsystem_pbus")
case object FBUS extends TLBusWrapperLocation("subsystem_fbus")
case object MBUS extends TLBusWrapperLocation("subsystem_mbus")
case object CBUS extends TLBusWrapperLocation("subsystem_cbus")
case object L2   extends TLBusWrapperLocation("subsystem_l2")

/** Parameterizes the subsystem in terms of optional clock-crossings
  *   that are insertable between some of the five traditional tilelink bus wrappers.
  *   This class exists for backwards compatiblity reasons but could eventually be retired
  *   in favor of manually filling in crossing types within each custom TLBusWrapperTopology.
  */
case class SubsystemCrossingParams(
  sbusToCbusXType: ClockCrossingType = NoCrossing,
  cbusToPbusXType: ClockCrossingType = SynchronousCrossing(),
  fbusToSbusXType: ClockCrossingType = SynchronousCrossing()
)

// Taken together these case classes provide a backwards-compatibility parameterization
//  of a bus topology that contains the five traditional tilelink bus wrappers.
//  Users desiring a different topology are free to define a similar subclass,
//  or just populate an instance of TLBusWrapperTopology via some other mechanism.

/** Parameterization of a topology containing a single bus named "subsystem_sbus". */
case class JustOneBusTopologyParams(
  sbus: SystemBusParams,
) extends TLBusWrapperTopology(
  instantiations = List((SBUS, sbus)),
  connections = Nil
)

/** Parameterization of a topology containing three additional, optional buses for attaching MMIO devices. */
case class HierarchicalBusTopologyParams(
  pbus: PeripheryBusParams,
  fbus: FrontBusParams,
  cbus: PeripheryBusParams,
  xTypes: SubsystemCrossingParams
) extends TLBusWrapperTopology(
  instantiations = List(
    (PBUS, pbus),
    (FBUS, fbus),
    (CBUS, cbus)),
  connections = List(
    (SBUS, CBUS, TLBusWrapperConnection  .crossTo(xTypes.sbusToCbusXType)),
    (CBUS, PBUS, TLBusWrapperConnection  .crossTo(xTypes.cbusToPbusXType)),
    (FBUS, SBUS, TLBusWrapperConnection.crossFrom(xTypes.fbusToSbusXType)))
)

/** Parameterization of a topology containing a banked coherence manager and a bus for attaching memory devices. */
case class CoherentBusTopologyParams(
  sbus: SystemBusParams, // TODO remove this after better width propagation
  mbus: MemoryBusParams,
  l2: BankedL2Params
) extends TLBusWrapperTopology(
  instantiations = (if (l2.nBanks == 0) Nil else List(
    (MBUS, mbus),
    (L2, CoherenceManagerWrapperParams(mbus.blockBytes, mbus.beatBytes, l2.nBanks, L2.name)(l2.coherenceManager)))),
  connections = if (l2.nBanks == 0) Nil else List(
    (SBUS, L2,   TLBusWrapperConnection(driveClockFromMaster = Some(true), nodeBinding = BIND_STAR)()),
    (L2,  MBUS,  TLBusWrapperConnection(driveClockFromMaster = Some(true), nodeBinding = BIND_QUERY)())
  )
)
