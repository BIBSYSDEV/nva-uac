package no.sikt.nva.uac.migration.organisation;

import java.util.Set;

/**
 * What a reconciliation found: counts in staging against counts loaded, and the staged rows that
 * cannot or did not make it across. Clean when every count matches and every set is empty.
 *
 * @param stagedUnits units in staging
 * @param loadedUnits units in the real table
 * @param stagedNames names in staging
 * @param loadedNames labels in the real table
 * @param unitsNotLoaded staged identifiers absent from the real table
 * @param parentsNotStaged parents named in staging that are not staged themselves
 * @param unitsWithoutName staged units with no staged name in any language
 */
public record UnitReconciliationReport(
    long stagedUnits,
    long loadedUnits,
    long stagedNames,
    long loadedNames,
    Set<String> unitsNotLoaded,
    Set<String> parentsNotStaged,
    Set<String> unitsWithoutName) {

  private static final int SHOWN_AT_MOST = 20;

  public UnitReconciliationReport {
    unitsNotLoaded = Set.copyOf(unitsNotLoaded);
    parentsNotStaged = Set.copyOf(parentsNotStaged);
    unitsWithoutName = Set.copyOf(unitsWithoutName);
  }

  /** True when staging and the real tables agree and nothing staged is unloadable. */
  public boolean isClean() {
    return stagedUnits == loadedUnits
        && stagedNames == loadedNames
        && unitsNotLoaded.isEmpty()
        && parentsNotStaged.isEmpty()
        && unitsWithoutName.isEmpty();
  }

  /** The report as Markdown, for the rehearsal record. */
  public String asMarkdown() {
    return String.join(
        "\n",
        "## Units",
        "",
        "| Measure | Staged | Loaded |",
        "|---|---|---|",
        "| Units | " + stagedUnits + " | " + loadedUnits + " |",
        "| Names | " + stagedNames + " | " + loadedNames + " |",
        "",
        "- Units not loaded: " + describe(unitsNotLoaded),
        "- Parents not staged: " + describe(parentsNotStaged),
        "- Units without a name: " + describe(unitsWithoutName),
        "",
        isClean() ? "Clean." : "Not clean.",
        "");
  }

  private static String describe(Set<String> identifiers) {
    return identifiers.isEmpty() ? "none" : countAndSample(identifiers);
  }

  private static String countAndSample(Set<String> identifiers) {
    var shown = identifiers.stream().sorted().limit(SHOWN_AT_MOST).toList();
    var more = identifiers.size() - shown.size();
    return identifiers.size() + " (" + String.join(", ", shown) + (more > 0 ? ", …" : "") + ")";
  }
}
