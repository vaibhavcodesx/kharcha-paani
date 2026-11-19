// One drawn icon set: 24px grid, 1.75 stroke, round caps. Paths are authored here, not borrowed glyphs.

const PATHS = {
  today: 'M3 17h18M7 17a5 5 0 0 1 10 0M12 5v3M5.6 8.6l1.8 1.8M18.4 8.6l-1.8 1.8',
  people: 'M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM2.5 20c.6-3.4 3.2-5 6.5-5s5.9 1.6 6.5 5M16.5 4.4a3.5 3.5 0 0 1 0 6.2M18 15.2c2 .5 3.2 2 3.6 4.8',
  limits: 'M4 7h9M17 7h3M4 17h3M11 17h9M15 4v6M9 14v6',
  release: 'M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM11 18h2',
  log: 'M5 4h14v16H5zM9 9h6M9 13h6M9 17h3',
  moon: 'M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z',
  sun: 'M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4',
  logout: 'M10 4H5v16h5M15 8l4 4-4 4M19 12H9',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  warn: 'M12 3.5l9.5 16.5h-19L12 3.5zM12 10v4.5M12 17.5v.01',
  check: 'M5 12.5l4.5 4.5L19 7.5',
  pause: 'M8.5 5v14M15.5 5v14',
} as const

export type IconName = keyof typeof PATHS

export function Icon({ name }: { name: IconName }) {
  return (
    <svg className="icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path d={PATHS[name]} />
    </svg>
  )
}
