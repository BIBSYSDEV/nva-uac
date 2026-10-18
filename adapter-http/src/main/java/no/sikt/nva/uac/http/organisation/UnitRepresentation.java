package no.sikt.nva.uac.http.organisation;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import no.sikt.nva.uac.organisation.Unit;
import no.sikt.nva.uac.shared.LanguageTag;

/**
 * The JSON representation of a unit.
 *
 * @param id the dotted identifier
 * @param name the name by language tag, in tag order
 * @param acronym the acronym, absent when the unit has none
 * @param partOf the dotted identifier of the parent, absent for an institution
 */
public record UnitRepresentation(
    String id, SortedMap<String, String> name, Optional<String> acronym, Optional<String> partOf) {

  /** Projects a unit onto its representation. */
  public static UnitRepresentation from(Unit unit) {
    return new UnitRepresentation(
        unit.identifier().toString(),
        nameByLanguageTag(unit.name().byLanguage()),
        unit.acronym(),
        unit.partOf().map(Object::toString));
  }

  private static SortedMap<String, String> nameByLanguageTag(Map<LanguageTag, String> name) {
    var byTag =
        name.entrySet().stream()
            .collect(Collectors.toMap(entry -> entry.getKey().value(), Map.Entry::getValue));
    return Collections.unmodifiableSortedMap(new TreeMap<>(byTag));
  }
}
