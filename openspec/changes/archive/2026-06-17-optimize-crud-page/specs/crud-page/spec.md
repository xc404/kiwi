## ADDED Requirements

### Requirement: PageConfig edit modal uses columns for layout

The system SHALL expose the number of form columns in the edit modal configuration under the property name `columns` (not a misspelling of "columns").

#### Scenario: Developer sets edit modal column count

- **WHEN** a consumer passes `editModal: { columns: 3 }` in `PageConfig`
- **THEN** the edit form receives a column count of 3 for layout purposes

#### Scenario: Default column count when omitted

- **WHEN** `editModal` is omitted or `columns` is not specified
- **THEN** the CRUD page SHALL default to two columns for the edit form layout
