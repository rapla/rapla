package org.rapla.plugin.adminpanels;

/** The widget kind for an admin / preferences field. Each value has a fixed
 *  wire-format (see PRD 020 §"Wire contract"). The renderer switches on this
 *  enum to pick a Swing component.
 *
 *  <p>v1 ships 10 types. Additional types ({@code MULTI_SELECT}, {@code TIME},
 *  {@code DATE}, {@code CATEGORY_PICK}, {@code ALLOCATABLE_PICK},
 *  {@code USER_PICK}, {@code DYNAMIC_TYPE_PICK}, {@code TABLE}) are added on
 *  demand when a future panel migration needs them. {@link #JSON_EDITOR} is
 *  the universal escape hatch in the meantime. */
public enum FieldType
{
    /** {@link javax.swing.JCheckBox}. Wire value: {@link Boolean}. */
    BOOL,

    /** Single-line text. {@link javax.swing.JTextField}. Wire value: {@link String}. */
    TEXT,

    /** Multi-line text. {@link javax.swing.JTextArea}. Wire value: {@link String}. */
    LONG_TEXT,

    /** Password text — masked client-side. {@link javax.swing.JPasswordField}.
     *  Wire value: {@link String} (cleartext over the existing TLS-protected
     *  REST surface; the server may need it raw, e.g. to test SMTP login). */
    PASSWORD,

    /** Integer. {@link javax.swing.JSpinner}. Wire value: {@link Number}.
     *  {@code typeConfig}: optional {@code min}, {@code max}, {@code step}. */
    INT,

    /** Single-choice dropdown. {@link javax.swing.JComboBox}. Wire value:
     *  {@link String} (the chosen value).
     *  {@code typeConfig}: required {@code options}: {@code List<{value, label}>}. */
    SELECT,

    /** Single-choice radio buttons. Wire value: {@link String} (the chosen value).
     *  {@code typeConfig}: required {@code options}: {@code List<{value, label}>}. */
    RADIO_GROUP,

    /** Read-only display. {@link javax.swing.JLabel}. Wire value: {@link String}
     *  (server-computed). {@code Field.readOnly} is implicitly true. */
    DISPLAY_ONLY,

    /** Action trigger. {@link javax.swing.JButton}. No wire value; the button
     *  fires {@link ActionButton} via the action endpoint. */
    ACTION_BUTTON,

    /** JSON-shaped structured config edited as text. {@link javax.swing.JTextArea}
     *  with parse-on-save validation. Wire value: {@link String} (raw JSON).
     *  Server parses + validates server-side and translates to its native
     *  storage shape (e.g. RaplaConfiguration tree).
     *  {@code typeConfig}: optional {@code schemaHint}: short description of
     *  the expected shape, shown as help text. */
    JSON_EDITOR
}
