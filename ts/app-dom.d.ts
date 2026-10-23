// Expando the app sets on its own dropdown rows (ts/app.ts renderDropdown). Never served.
interface HTMLLIElement {
  /** The catalogue city this row stands for. */
  _city?: City;
}
