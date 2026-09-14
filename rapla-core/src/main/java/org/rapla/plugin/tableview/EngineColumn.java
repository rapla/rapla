package org.rapla.plugin.tableview;

/**
 * Input to {@link TableViewEngine#project}: pairs a wire-shipped
 * {@link TableColumnDescriptor} with the {@link CellExtractor} that
 * computes the cell value for a row of type {@code T}.
 *
 * <p>The descriptor is what the client sees; the extractor is the
 * server-side function. Bundling them keeps the engine's input list
 * tight ("here are the columns") and lets the engine emit the
 * descriptors verbatim in {@link TablePage#columns()}.
 */
public record EngineColumn<T>(TableColumnDescriptor descriptor, CellExtractor<T> extractor)
{
    public EngineColumn
    {
        if (descriptor == null) throw new IllegalArgumentException("descriptor must not be null");
        if (extractor == null) throw new IllegalArgumentException("extractor must not be null");
    }
}
