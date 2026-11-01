package no.sikt.nva.uac.organisation;

import java.util.List;
import java.util.Optional;
import no.sikt.nva.uac.shared.Labels;
import no.sikt.nva.uac.shared.LanguageTag;
import no.sikt.nva.uac.shared.LanguageTaggedText;

/** Test data: well-formed units, named in Norwegian Bokmål. */
public final class Units {

  private static final LanguageTag NORWEGIAN_BOKMAAL = new LanguageTag("nb");

  private Units() {
    // NO-OP
  }

  /** An institution with the given dotted identifier and name, without an acronym. */
  public static Unit institution(String dottedIdentifier, String name) {
    return new Unit(
        UnitIdentifier.parse(dottedIdentifier), named(name), Optional.empty(), Optional.empty());
  }

  /** A unit with the given dotted identifier and name, directly under the given parent. */
  public static Unit unitWithin(Unit parent, String dottedIdentifier, String name) {
    return new Unit(
        UnitIdentifier.parse(dottedIdentifier),
        named(name),
        Optional.empty(),
        Optional.of(parent.identifier()));
  }

  private static Labels named(String name) {
    return Labels.from(List.of(new LanguageTaggedText(NORWEGIAN_BOKMAAL, name)));
  }
}
