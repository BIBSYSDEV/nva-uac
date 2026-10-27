package no.sikt.nva.uac.migration.organisation;

import java.util.Set;

/**
 * What a reconciliation found: counts in staging against counts loaded, the staged rows that cannot
 * or did not make it across, and where the two staged directions of the hierarchy disagree. Clean
 * when every count matches and every set is empty.
 *
 * @param stagedUnits units in staging
 * @param loadedUnits units in the real table
 * @param stagedNames names in staging
 * @param loadedNames labels in the real table
 * @param malformedIdentifiers staged identifiers not in the required form, masked
 * @param unitsNotLoaded staged identifiers absent from the real table
 * @param parentsNotStaged parents named in staging that are not staged themselves
 * @param unitsWithoutName staged units with no staged name in any language
 * @param namesWithoutUnit identifiers with a staged name but no staged unit
 * @param childrenNotListedByParent staged units whose claimed parent does not list them as a part
 * @param listedChildrenClaimingAnotherParent listings, as {@code parent -> child}, whose child
 *     claims a different parent, or none, or is not staged
 */
public record UnitReconciliationReport(
    long stagedUnits,
    long loadedUnits,
    long stagedNames,
    long loadedNames,
    Set<String> malformedIdentifiers,
    Set<String> unitsNotLoaded,
    Set<String> parentsNotStaged,
    Set<String> unitsWithoutName,
    Set<String> namesWithoutUnit,
    Set<String> childrenNotListedByParent,
    Set<String> listedChildrenClaimingAnotherParent) {

  private static final int SHOWN_AT_MOST = 20;
  private static final String COUNT_ROW = "| %s | %s | %s |";
  private static final String FINDING = "- %s: %s";
  private static final String CLEAN = "Clean.";
  private static final String NOT_CLEAN = "Not clean.";

  public UnitReconciliationReport {
    malformedIdentifiers = Set.copyOf(malformedIdentifiers);
    unitsNotLoaded = Set.copyOf(unitsNotLoaded);
    parentsNotStaged = Set.copyOf(parentsNotStaged);
    unitsWithoutName = Set.copyOf(unitsWithoutName);
    namesWithoutUnit = Set.copyOf(namesWithoutUnit);
    childrenNotListedByParent = Set.copyOf(childrenNotListedByParent);
    listedChildrenClaimingAnotherParent = Set.copyOf(listedChildrenClaimingAnotherParent);
  }

  /**
   * True when staging and the real tables agree, nothing staged is unloadable, and every parent
   * lists exactly the children that claim it.
   */
  public boolean isClean() {
    return stagedUnits == loadedUnits
        && stagedNames == loadedNames
        && malformedIdentifiers.isEmpty()
        && unitsNotLoaded.isEmpty()
        && parentsNotStaged.isEmpty()
        && unitsWithoutName.isEmpty()
        && namesWithoutUnit.isEmpty()
        && childrenNotListedByParent.isEmpty()
        && listedChildrenClaimingAnotherParent.isEmpty();
  }

  /** The report as Markdown, for the rehearsal record. */
  public String asMarkdown() {
    return String.join(
        "\n",
        "## Units",
        "",
        COUNT_ROW.formatted("Measure", "Staged", "Loaded"),
        COUNT_ROW.formatted("---", "---", "---"),
        COUNT_ROW.formatted("Units", stagedUnits, loadedUnits),
        COUNT_ROW.formatted("Names", stagedNames, loadedNames),
        "",
        FINDING.formatted("Malformed identifiers", describe(malformedIdentifiers)),
        FINDING.formatted("Units not loaded", describe(unitsNotLoaded)),
        FINDING.formatted("Parents not staged", describe(parentsNotStaged)),
        FINDING.formatted("Units without a name", describe(unitsWithoutName)),
        FINDING.formatted("Names without a unit", describe(namesWithoutUnit)),
        FINDING.formatted(
            "Children their parent does not list", describe(childrenNotListedByParent)),
        FINDING.formatted(
            "Listed children claiming another parent",
            describe(listedChildrenClaimingAnotherParent)),
        "",
        isClean() ? CLEAN : NOT_CLEAN,
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
