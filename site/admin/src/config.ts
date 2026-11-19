// The project URL and the publishable key. Both are public by design: the key identifies the project and grants
// nothing by itself (it is also in site/split.html and the app). Every admin action is checked in the database.
export const SUPABASE_URL = 'https://tnaehyxzrudpaaoigoak.supabase.co'
export const SUPABASE_PUBLISHABLE_KEY = 'sb_publishable_g4bm2y38HtklmQxaBDBNQw_kEztFnAo'

/** Signed out after this long without a touch, key press or scroll. */
export const IDLE_SIGN_OUT_MS = 20 * 60 * 1000

/** Supabase free-plan limits the Today screen measures against. */
export const FREE_STORAGE_BYTES = 1024 ** 3
export const FREE_DATABASE_BYTES = 500 * 1024 ** 2
