package org.jabref.model.entry;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.SequencedSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.jabref.model.database.BibDatabaseMode;
import org.jabref.model.entry.field.BibField;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.FieldPriority;
import org.jabref.model.entry.field.OrFields;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.EntryType;

public class BibEntryType implements Comparable<BibEntryType> {

    private final EntryType type;
    private final SequencedSet<BibField> allFields;
    private final SequencedSet<OrFields> requiredFields;

    /**
     * Provides an enriched EntryType with information about defined standards as mandatory fields etc.
     *
     * A builder is available at {@link BibEntryTypeBuilder}
     *
     * @param type              The EntryType this BibEntryType is wrapped around.
     * @param allFields         A BibFields list of all fields, including the required fields
     * @param requiredFields    A OrFields list of just the required fields
     */
    public BibEntryType(EntryType type, Collection<BibField> allFields, Collection<OrFields> requiredFields) {
        this.type = Objects.requireNonNull(type);
        this.allFields = new LinkedHashSet<>(allFields);
        this.requiredFields = new LinkedHashSet<>(requiredFields);
    }

    public EntryType getType() {
        return type;
    }

    public SequencedSet<BibField> getOptionalFields() {
        return getAllBibFields().stream()
                             .filter(field -> !isRequired(field.field()))
                             .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean isRequired(Field field) {
        return getRequiredFields().stream()
                                  .anyMatch(fields -> fields.contains(field));
    }

    /**
     * Returns all required field names.
     * If fields have an OR relationship the name includes both field names divided by /, e.g. author/editor.
     * If you need all required fields as sole entities use @see{getRequiredFieldsFlat} .
     *
     * @return a Set of required field name Strings
     */
    public SequencedSet<OrFields> getRequiredFields() {
        return Collections.unmodifiableSequencedSet(requiredFields);
    }

    /**
     * Returns all defined fields.
     */
    public SequencedSet<BibField> getAllBibFields() {
        return Collections.unmodifiableSequencedSet(allFields);
    }

    public Set<Field> getAllFields() {
        return allFields.stream().map(BibField::field).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public SequencedSet<Field> getImportantOptionalFields() {
        return getOptionalFields().stream()
                                  .filter(field -> field.priority() == FieldPriority.IMPORTANT)
                                  .map(BibField::field)
                                  .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public SequencedSet<Field> getDetailOptionalFields() {
        return getOptionalFields().stream()
                                  .filter(field -> field.priority() == FieldPriority.DETAIL)
                                  .map(BibField::field)
                                  .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<Field> getDeprecatedFields(BibDatabaseMode mode) {
        if (mode == BibDatabaseMode.BIBTEX) {
            return Set.of();
        }
        Set<Field> deprecatedFields = new HashSet<>(EntryConverter.FIELD_ALIASES_BIBTEX_TO_BIBLATEX.keySet());

        // Only the optional fields which are mapped to another BibLaTeX name should be shown as "deprecated"
        deprecatedFields.retainAll(getOptionalFieldsAndAliases());

        // BibLaTeX aims for that field "date" is used
        // Thus, year + month is deprecated
        // However, year is used in the wild very often, so we do not mark that as default as deprecated
        deprecatedFields.add(StandardField.MONTH);

        return deprecatedFields;
    }

    public SequencedSet<Field> getDetailOptionalNotDeprecatedFields(BibDatabaseMode mode) {
        SequencedSet<Field> optionalFieldsNotPrimaryOrDeprecated = new LinkedHashSet<>(getDetailOptionalFields());
        optionalFieldsNotPrimaryOrDeprecated.removeAll(getDeprecatedFields(mode));
        return optionalFieldsNotPrimaryOrDeprecated;
    }

    /**
     * Get list of all optional fields of this entry and all fields being source for a BibTeX to BibLaTeX conversion.
     */
    private SequencedSet<Field> getOptionalFieldsAndAliases() {
        SequencedSet<Field> optionalFieldsAndAliases = new LinkedHashSet<>(getOptionalFields().size());
        for (BibField field : getOptionalFields()) {
            optionalFieldsAndAliases.add(field.field());
            if (EntryConverter.FIELD_ALIASES_BIBTEX_TO_BIBLATEX.containsKey(field.field())) {
                optionalFieldsAndAliases.add(field.field());
            }
        }
        return optionalFieldsAndAliases;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        BibEntryType that = (BibEntryType) o;
        return type.equals(that.type) &&
               Objects.equals(requiredFields, that.requiredFields) &&
               Objects.equals(allFields, that.allFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, requiredFields, allFields);
    }

    /// Generates a **single line** string containing the information. This is used for debugging purposes.
    /// See "<a href="https://biratkirat.medium.com/learning-effective-java-item-10-84cc3ab553bc">Effective Java, Item 10</a>" for a discussion on contracts.
    /// We are sure, we are using this method in a) logs (which should use single lines for output) and b) in the UI. For the UI, we use [org.jabref.gui.importer.BibEntryTypePrefsAndFileViewModel],
    /// which in turn uses [#serializeCustomEntryTypes(BibEntryType)]
    @Override
    public String toString() {
        return "BibEntryType{" +
                "type=" + type +
                ", allFields=" + allFields +
                ", requiredFields=" + requiredFields +
                '}';
    }

    /**
     * WARNING! This does not follow the equals contract. Even if this here returns 0, the objects can be different.
     */
    @Override
    public int compareTo(BibEntryType o) {
        return this.getType().getName().compareTo(o.getType().getName());
    }
}
