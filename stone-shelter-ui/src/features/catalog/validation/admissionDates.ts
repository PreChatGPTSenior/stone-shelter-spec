export function validateAdmissionDates(from: string, to: string): string | undefined {
  for (const value of [from, to]) {
    if (value && (!/^\d{4}-\d{2}-\d{2}$/.test(value)
      || Number.isNaN(Date.parse(`${value}T00:00:00Z`))
      || new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) !== value)) {
      return 'Enter a valid date in YYYY-MM-DD format.';
    }
  }
  if (from && to && from > to) return 'From must be on or before To.';
}
