import {
  ArrowClockwise, ArrowRight, CheckCircle, ClockCounterClockwise, Command, DeviceMobile, Faders, HardDrives, Image,
  MagnifyingGlass, Moon, PauseCircle, SignOut, Sun, SunHorizon, Trash, UsersThree, WarningCircle, X,
  type Icon as PhosphorIcon,
} from '@phosphor-icons/react'

// One icon family (Phosphor), one stroke weight. The page asks for icons by the meaning they carry here.

const ICONS = {
  today: SunHorizon,
  people: UsersThree,
  limits: Faders,
  release: DeviceMobile,
  log: ClockCounterClockwise,
  moon: Moon,
  sun: Sun,
  logout: SignOut,
  arrow: ArrowRight,
  warn: WarningCircle,
  check: CheckCircle,
  pause: PauseCircle,
  search: MagnifyingGlass,
  trash: Trash,
  storage: HardDrives,
  photo: Image,
  close: X,
  command: Command,
  refresh: ArrowClockwise,
} satisfies Record<string, PhosphorIcon>

export type IconName = keyof typeof ICONS

export function Icon({ name, filled, size = 22 }: { name: IconName; filled?: boolean; size?: number }) {
  const Glyph = ICONS[name]
  return <Glyph className="icon" size={size} weight={filled ? 'fill' : 'regular'} aria-hidden="true" focusable="false" />
}
