/*
 * Copyright (C) 2005 David Orme <djo@coconut-palm-software.com>
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     David Orme     - Initial API and implementation
 *     Elias Volanakis - 267316
 */
package moe.lymia.nebula.compositetable;

import moe.lymia.nebula.compositetable.internal.DuckType;
import moe.lymia.nebula.compositetable.internal.ISelectableRegionControl;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * (non-API) Class InternalCompositeTable. This is the run-time
 * CompositeTableControl. It gets its prototype row and (optional) header
 * objects from its SWT parent, then uses them to implement an SWT virtual table
 * control.
 *
 * @author djo
 */
class InternalCompositeTable extends Composite implements Listener {
    // The internal UI controls that make up this control.
    private Composite controlHolder = null;
    private Composite vSliderHolder = null;
    private Slider vSlider = null;
    private Composite hScroller;
    private Composite hSliderHolder = null;
    private Slider hSlider = null;
    EmptyTablePlaceholder emptyTablePlaceholder = null;

    // My parent CompositeTable
    private CompositeTable parent;

    // Property fields
    private int maxRowsVisible;
    private int numRowsInDisplay;
    private int numRowsInCollection;
    private int topRow;
    private int currentRow;
    private int currentColumn;

    // The visible/invisible row objects and bookeeping info about them
    private int currentVisibleTopRow = 0;
    private int numRowsVisible = 0;
    private LinkedList<TableRow> rows = new LinkedList<TableRow>();
    private LinkedList<TableRow> spareRows = new LinkedList<TableRow>();
    int clientAreaHeight;

    // The prototype header/row objects and Constructors so we can duplicate
    // them
    private Constructor<Control> headerConstructor;
    private Constructor<Control> rowConstructor;
    private Control headerControl;
    private Control myHeader = null;
    private Control rowControl;

    /**
     * Constructor InternalCompositeTable. The usual SWT constructor. The same
     * style bits are allowed here as are allowed on Composite.
     *
     * @param parentControl The SWT parent.
     * @param style         Style bits.
     */
    public InternalCompositeTable(Composite parentControl, int style) {
        super(parentControl, style);
        initialize();

        this.parent = (CompositeTable) parentControl;

        setBackground(parentControl.getBackground());
        controlHolder.addListener(SWT.MouseWheel, this);

        maxRowsVisible = parent.getMaxRowsVisible();
        numRowsInCollection = parent.getNumRowsInCollection();
        topRow = parent.getTopRow();

        headerConstructor = parent.getHeaderConstructor();
        rowConstructor = parent.getRowConstructor();
        headerControl = parent.getHeaderControl();
        rowControl = parent.getRowControl();

        setMenu(parent.getMenu());

        currentVisibleTopRow = topRow;
        showHeader();
        updateVisibleRows();

        if (numRowsVisible < 1) {
            createEmptyTablePlaceholer();
        }
        currentRow = -1; // initialize to undefined
    }

    public void setBackground(Color color) {
        super.setBackground(color);
        controlHolder.setBackground(color);
    }

    /**
     * Initialize the overall table UI.
     */
    private void initialize() {
        GridLayout gl = new GridLayout();
        gl.numColumns = 2;
        gl.verticalSpacing = 0;
        gl.marginWidth = 0;
        gl.marginHeight = 0;
        // borderWidth times two, since border steals pixels from both sides
        int borderInPixels = getParent().getBorderWidth() * 2;
        gl.marginRight = borderInPixels;
        gl.marginBottom = borderInPixels;
        gl.horizontalSpacing = 0;
        this.setLayout(gl);
        createControlHolder();
        createVSliderHolder();
        createHSliderHolder();
    }

    /**
     * Initialize the controlHolder, which is the holder Composite for the
     * header object (if applicable) and the row objects.
     */
    private void createControlHolder() {
        hScroller = new Composite(this, SWT.NULL);
        GridData gridData = new GridData();
        gridData.horizontalAlignment = GridData.FILL;
        gridData.grabExcessHorizontalSpace = true;
        gridData.grabExcessVerticalSpace = true;
        gridData.verticalAlignment = GridData.FILL;
        hScroller.setLayoutData(gridData);

        controlHolder = new Composite(hScroller, SWT.NONE);

        controlHolder.setLayout(new Layout() {
            protected Point computeSize(Composite composite, int wHint,
                                        int hHint, boolean flushCache) {
                if (rowControl != null) {
                    int height = 0;
                    int width = 0;
                    if (headerControl != null) {
                        Point headerSize = headerControl.computeSize(wHint, hHint, flushCache);
                        width = headerSize.x;
                        height = headerSize.y;
                    }
                    Point rowSize = rowControl.computeSize(wHint, hHint, flushCache);
                    height += rowSize.y * 2;
                    if (width < rowSize.x) {
                        width = rowSize.x;
                    }
                    return new Point(width, height);
                }
                return new Point(50, 50);
            }

            protected void layout(Composite composite, boolean flushCache) {
                layoutControlHolder();
            }
        });

        hScroller.addControlListener(scrollerResizeHandler);
    }

    ControlAdapter scrollerResizeHandler = new ControlAdapter() {
        public void controlResized(ControlEvent e) {
            Point size = hScroller.getSize();

            int preferredWidth = controlHolder.computeSize(SWT.DEFAULT,
                    SWT.DEFAULT, true).x;

            if (preferredWidth > size.x && !isHSliderVisible()) {
                setHSliderVisible(true);
            }
            if (preferredWidth <= size.x && isHSliderVisible()) {
                setHSliderVisible(false);
            }

            if (preferredWidth <= size.x) {
                controlHolder.setBounds(0, 0, size.x, size.y);
                return;
            }

            if (isHSliderVisible()) {
                hSlider.setMaximum(preferredWidth);
                hSlider.setPageIncrement(size.x);
                hSlider.setThumb(preferredWidth - (preferredWidth - size.x));
                int currentSelection = hSlider.getSelection();
                if (preferredWidth - currentSelection < size.x) {
                    hSlider.setSelection(preferredWidth - size.x);
                }
            }
            hSlider.notifyListeners(SWT.Selection, new Event());
        }
    };


    /**
     * Initialize the sliderHolder and slider. The SliderHolder is a Composite
     * that is responsible for showing and hiding the vertical slider upon
     * request.
     */
    private void createVSliderHolder() {
        GridData gd = getVSliderGridData();
        vSliderHolder = new Composite(this, SWT.NULL);
        vSlider = new Slider(vSliderHolder, SWT.VERTICAL);
        vSlider.addSelectionListener(sliderSelectionListener);
        vSliderHolder.setLayout(new FillLayout());
        vSliderHolder.setLayoutData(gd);
        vSliderHolder.setTabList(new Control[]{});
    }

    /**
     * Initialize the sliderHolder and slider. The SliderHolder is a Composite
     * that is responsible for showing and hiding the vertical slider upon
     * request.
     */
    private void createHSliderHolder() {
        GridData gd = getHSliderGridData();
        hSliderHolder = new Composite(this, SWT.NULL);
        hSlider = new Slider(hSliderHolder, SWT.HORIZONTAL);
        hSlider.setMinimum(0);
        hSlider.setIncrement(20);
        hSlider.addSelectionListener(sliderSelectionListener);
        hSliderHolder.setLayout(new FillLayout());
        hSliderHolder.setLayoutData(gd);
        hSliderHolder.setTabList(new Control[]{});
        hSlider.addSelectionListener(hSliderSelectionListener);
    }

    // Slider utility methods
    // ---------------------------------------------------------------------

    /**
     * Returns a GridData for the SliderHolder appropriate for if the slider is
     * visible or not.
     *
     * @return A GridData with a widthHint of 0 if the slider is not visible or
     * with a widthHint of SWT.DEFAULT otherwise.
     */
    private GridData getVSliderGridData() {
        GridData gd = new GridData();
        gd.grabExcessVerticalSpace = true;
        gd.verticalAlignment = GridData.FILL;
        gd.verticalSpan = 1;
        if (!vSliderVisible) {
            gd.widthHint = 0;
        }
        return gd;
    }

    /**
     * Returns a GridData for the SliderHolder appropriate for if the slider is
     * visible or not.
     *
     * @return A GridData with a heightHint of 0 if the slider is not visible or
     * with a heightHint of SWT.DEFAULT otherwise.
     */
    private GridData getHSliderGridData() {
        GridData gd = new GridData();
        gd.grabExcessHorizontalSpace = true;
        gd.horizontalAlignment = GridData.FILL;
        gd.horizontalSpan = 1;
        if (!hSliderVisible) {
            gd.heightHint = 0;
        }
        return gd;
    }

    private boolean vSliderVisible = false;

    /**
     * Sets if the slider is visible or not.
     *
     * @param visible true if the slider should be visible; false otherwise.
     */
    public void setVSliderVisible(boolean visible) {
        this.vSliderVisible = visible;
        vSliderHolder.setLayoutData(getVSliderGridData());
        if (visible) {
            Display.getCurrent().addFilter(SWT.KeyDown, displayKeyDownFilter);
        } else {
            Display.getCurrent().removeFilter(SWT.KeyDown, displayKeyDownFilter);
        }
        Display.getCurrent().asyncExec(new Runnable() {
            public void run() {
                if (!InternalCompositeTable.this.isDisposed() &&
                        !vSliderHolder.isDisposed() &&
                        !vSliderHolder.getParent().isDisposed()) {
                    vSliderHolder.getParent().layout(true);
                    vSliderHolder.layout(true);
                    Point sliderHolderSize = vSliderHolder.getSize();
                    vSlider.setBounds(0, 0, sliderHolderSize.x, sliderHolderSize.y);
                }
            }
        });
    }

    /**
     * Returns if the slider is visible.
     *
     * @return true if the slider is visible; false otherwise.
     */
    public boolean isVSliderVisible() {
        return vSliderVisible;
    }

    private boolean hSliderVisible = false;

    /**
     * Sets if the slider is visible or not.
     *
     * @param visible true if the slider should be visible; false otherwise.
     */
    public void setHSliderVisible(boolean visible) {
        this.hSliderVisible = visible;
        hSliderHolder.setLayoutData(getHSliderGridData());
        Display.getCurrent().asyncExec(new Runnable() {
            public void run() {
                if (!InternalCompositeTable.this.isDisposed() &&
                        !hSliderHolder.isDisposed() &&
                        !hSliderHolder.getParent().isDisposed()) {
                    hSliderHolder.getParent().layout(true);
                    hSliderHolder.layout(true);
                    Point sliderHolderSize = hSliderHolder.getSize();
                    hSlider.setBounds(0, 0, sliderHolderSize.x, sliderHolderSize.y);
                }
            }
        });
    }

    /**
     * Returns if the slider is visible.
     *
     * @return true if the slider is visible; false otherwise.
     */
    public boolean isHSliderVisible() {
        return hSliderVisible;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.swt.widgets.Widget#dispose()
     */
    public void dispose() {
        disposeRows(rows);
        disposeRows(spareRows);
        super.dispose();
    }

    /**
     * Disposes all the row objects in the specified LinkedList.
     *
     * @param rowsCollection The collection containing TableRow objects to dispose.
     */
    private void disposeRows(LinkedList<TableRow> rowsCollection) {
        for (Iterator<TableRow> rowsIter = rowsCollection.iterator(); rowsIter.hasNext(); ) {
            TableRow row = rowsIter.next();
            if (row instanceof IRowFocusListener) {
                parent.removeRowFocusListener((IRowFocusListener) row);
            }
            if (row instanceof IRowContentProvider) {
                parent.removeRowContentProvider((IRowContentProvider) row);
            }
            row.dispose();
        }
    }

    // Row object layout
    // --------------------------------------------------------------------------

    /**
     * Layout the child controls within the controlHolder Composite.
     */
    protected void layoutControlHolder() {
        if (myHeader != null) {
            layoutHeaderOrRow(myHeader);
        }
        for (Iterator<TableRow> rowsIter = rows.iterator(); rowsIter.hasNext(); ) {
            TableRow row = rowsIter.next();
            layoutHeaderOrRow(row.getRowControl());
        }
        updateVisibleRows();
    }

    private void layoutHeaderOrRow(Control control) {
        if (control instanceof Composite) {
            Composite headerOrRow = (Composite) control;
            headerOrRow.layout(true);
        }
    }

    // Table control layout -- utility methods
    // ----------------------------------------------------

    /**
     * Construct a header or row object on demand. Logs an error and returns
     * null on failure.
     *
     * @param parent      The SWT parent.
     * @param constructor The header or row object's constructor.
     * @return The constructed control or null if none could be constructed.
     */
    private Control createInternalControl(Composite parent,
                                          Constructor<Control> constructor) {
        Control result = null;
        try {
            if (!constructor.isAccessible()) {
                constructor.setAccessible(true);
            }
            result = constructor.newInstance(new Object[]{parent,
                    new Integer(SWT.NULL)});
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to construct control"); //$NON-NLS-1$
        }
        if (result instanceof IRowFocusListener) {
            this.parent.addRowFocusListener((IRowFocusListener) result);
        }
        if (result instanceof IRowContentProvider) {
            this.parent.addRowContentProvider((IRowContentProvider) result);
        }
        return result;
    }

    /**
     * If the header control hasn't been created yet, create and show it.
     */
    private void showHeader() {
        if (myHeader == null && headerConstructor != null) {
            myHeader = createInternalControl(controlHolder, headerConstructor);
            fireHeaderConstructionEvent(myHeader);
            if (myHeader instanceof Composite) {
                Composite headerComp = (Composite) myHeader;
                if (headerComp.getLayout() instanceof GridRowLayout) {
                    headerComp.addPaintListener(headerPaintListener);
                }
            }
            layoutHeaderOrRow(myHeader);
        }
    }

    // Table control layout -- main refresh algorithm
    // ---------------------------------------------

    /**
     * Main refresh algorithm entry point. This method refreshes everything in
     * the table:
     * <p>
     * <ul>
     * <li>Makes sure the correct number of rows are visible
     * <li>Makes sure each row has been refreshed with data from the underlying
     * model
     * </ul>
     */
    void updateVisibleRows() {
        // If we don't have our prototype row object yet, bail out
        if (rowControl == null) {
            return;
        }

        clientAreaHeight = controlHolder.getSize().y;
        if (clientAreaHeight <= 0) {
            return;
        }

        int topPosition = 0;

        int headerHeight = 0;
        if (myHeader != null) {
            headerHeight = headerControl.getSize().y + 3;
            clientAreaHeight -= headerHeight;
            topPosition += headerHeight;
        }
        numRowsInDisplay = clientAreaHeight / getRowHeight(clientAreaHeight);

        // Make sure we have something to lay out to begin with
        int userScrollDirection = 0;
        if (numRowsInCollection > 0) {
            numRowsVisible = numRowsInDisplay;

            disposeEmptyTablePlaceholder();

            int displayableRows = numRowsInCollection - topRow;
            if (numRowsVisible > displayableRows) {
                numRowsVisible = displayableRows;
            }
            if (numRowsVisible > maxRowsVisible) {
                numRowsVisible = maxRowsVisible;
            }
            if (numRowsVisible < 1) {
                numRowsVisible = 1;
            }

            // Keep track of if we're scrolling forwards or backwards
            if (currentVisibleTopRow - topRow > 0) {
                userScrollDirection = ScrollEvent.BACKWARD;
            } else if (topRow - currentVisibleTopRow > 0) {
                userScrollDirection = ScrollEvent.FORWARD;
            }

            // Scroll the view so that the right number of row
            // objects are showing and they have the right data
            if (rows.size() - Math.abs(currentVisibleTopRow - topRow) > 0) {
//				if (currentRow >= numRowsVisible) {
//					deleteRowAt(0);
//					++currentVisibleTopRow;
//					++topRow;
//					--currentRow;
//				}
                scrollTop();
                fixNumberOfRows();
            } else {
                currentVisibleTopRow = topRow;
                fixNumberOfRows();
                refreshAllRows();
            }
        } else {
            numRowsVisible = 0;
            topRow = 0;
            currentRow = 0;
            currentColumn = 0;
            currentVisibleTopRow = 0;
            numRowsVisible = 0;

            if (emptyTablePlaceholder == null) {
                fixNumberOfRows();
                createEmptyTablePlaceholer();
            }
        }

        // Make sure that the currentRow is within the visible range
        // (after PgDn, it could wind up outside the visible range)
        if (currentRow >= numRowsVisible && getNumRowsVisible() < numRowsInDisplay) {
            currentRow = numRowsVisible - 1;
        }

        // Show, hide, reset the scroll bar
        if (numRowsVisible < numRowsInCollection) {
            int extra = numRowsInCollection - numRowsVisible;
            int pageIncrement = numRowsVisible;
            if (pageIncrement > extra)
                pageIncrement = extra;

            vSlider.setMaximum(numRowsInCollection);
            vSlider.setMinimum(0);
            vSlider.setIncrement(1);
            vSlider.setPageIncrement(pageIncrement);
            vSlider.setThumb(numRowsInCollection
                    - (numRowsInCollection - numRowsVisible));

            vSlider.setSelection(topRow);

            if (!isVSliderVisible()) {
                setVSliderVisible(true);
            }
        } else {
            setVSliderVisible(false);
        }

        // Lay out the header and rows correctly in the display
        int width = controlHolder.getSize().x;

        // First, the header...
        if (myHeader != null) {
            myHeader.setBounds(0, 0, width, headerHeight);
        }

        // Make sure we have rows to lay out...
        if (numRowsInCollection < 1) {
            return;
        }

        // Now the rows.
        int rowHeight = getRowHeight(clientAreaHeight);

        // We have to move the controls front-to-back if we're scrolling
        // forwards and back-to-front if we're scrolling backwards to avoid ugly
        // screen refresh artifacts.
        if (userScrollDirection == ScrollEvent.FORWARD || userScrollDirection == ScrollEvent.NONE) {
            for (Iterator<TableRow> rowsIter = rows.iterator(); rowsIter.hasNext(); ) {
                TableRow row = rowsIter.next();
                Control rowControl = row.getRowControl();
                rowControl.setBounds(0, topPosition, width, rowHeight);
                layoutHeaderOrRow(rowControl);
                topPosition += rowHeight;
            }
        } else {
            ListIterator<TableRow> rowsIter = rows.listIterator();
            while (rowsIter.hasNext()) {
                rowsIter.next();
            }
            topPosition += rowHeight * (rows.size() - 1);
            while (rowsIter.hasPrevious()) {
                TableRow row = rowsIter.previous();
                Control rowControl = row.getRowControl();
                rowControl.setBounds(0, topPosition, width, rowHeight);
                layoutHeaderOrRow(rowControl);
                topPosition -= rowHeight;
            }
        }

        // If we scrolled, tell clients about it
        if (userScrollDirection != ScrollEvent.NONE) {
            fireScrollEvent(new ScrollEvent(userScrollDirection, parent));
        }
    }

    int getRowHeight(int clientAreaHeight) {
        int rowControlHeight = rowControl.getSize().y;
        if (maxRowsVisible == Integer.MAX_VALUE) {
            return rowControlHeight;
        }
        return rowControlHeight;
    }

    /**
     * Utility method: Makes sure that the currently visible top row is the same
     * as the top row specified in the TopRow property.
     */
    private void scrollTop() {
        while (currentVisibleTopRow < topRow) {
            deleteRowAt(0);
            ++currentVisibleTopRow;
        }
        while (currentVisibleTopRow > topRow) {
            --currentVisibleTopRow;
            insertRowAt(0);
        }
    }

    /**
     * Utility method: Makes sure that the number of rows that are visible
     * correspond with what should be visible given the table control's size,
     * where it is scrolled, and the number of rows in the underlying
     * collection.
     */
    private void fixNumberOfRows() {
        int numRows = rows.size();
        while (numRows > numRowsVisible) {
            deleteRowAt(numRows - 1);
            numRows = rows.size();
        }
        while (numRows < numRowsVisible) {
            insertRowAt(numRows);
            numRows = rows.size();
        }
    }

    /**
     * Fire the refresh event on all visible rows.
     */
    void refreshAllRows() {
        int row = 0;
        for (Iterator<TableRow> rowsIter = rows.iterator(); rowsIter.hasNext(); ) {
            TableRow rowControl = rowsIter.next();
            fireRefreshEvent(topRow + row, rowControl.getRowControl());
            ++row;
        }
        resetFocus();
    }

    void refreshRow(int row) {
        if (topRow > -1) {
            if (isRowVisible(row)) {
                fireRefreshEvent(row + topRow, (rows.get(row)).getRowControl());
            }
        }
    }

    private boolean isRowVisible(int row) {
        return row >= 0 && row < numRowsVisible;
    }

    /**
     * Make sure that something sane inside the table has focus.
     */
    private void resetFocus() {
        /*
		 * FEATURE IN WINDOWS: When we refresh all rows and one already has
		 * focus, Windows gets into a schizophrenic state where some part of
		 * Windows thinks that the current control has focus and another part of
		 * Windows thinks that the current control doesn't have focus.
		 * 
		 * The symptom is that the current control stops receiving events from
		 * Windows but Windows still thinks the current control has focus and so
		 * it won't give the control complete focus if you click on it.
		 * 
		 * The workaround is to set the focus away from the currently-focused
		 * control and to set it back.
		 */
        if (numRowsVisible < 1 || currentRow < 0) {
            return;
        }
        Control control = null;
        if (currentRow < numRowsVisible) {
            control = getControl(currentColumn, currentRow);
        } else if (currentRow > 0) {
            control = getControl(currentColumn, numRowsVisible - 1);
        }
        if (control != null && control.isFocusControl()) {
            this.setFocus();
            deferredSetFocus(control, true);
        }
    }

    /**
     * Insert a new row object at the specified 0-based position relatve to the
     * topmost row.
     *
     * @param position The 0-based position relative to the topmost row.
     */
    private void insertRowAt(int position) {
        TableRow newRow = getNewRow();
        if (position > rows.size()) {
            position = rows.size();
        }
        rows.add(position, newRow);
        fireRefreshEvent(currentVisibleTopRow + position, newRow
                .getRowControl());
    }

    /**
     * Delete the row at the specified 0-based position relative to the topmost
     * row.
     *
     * @param position The 0-based position relative to the topmost row.
     */
    private void deleteRowAt(int position) {
        TableRow row = rows.remove(position);
        row.setVisible(false);
        spareRows.addLast(row);
    }

    /**
     * Utility method: Creates a new row object or recycles one that had been
     * previously created but was no longer needed.
     *
     * @return The new row object.
     */
    private TableRow getNewRow() {
        if (spareRows.size() > 0) {
            TableRow recycledRow = spareRows.removeFirst();
            recycledRow.setVisible(true);
            return recycledRow;
        }
        Control newControl = createInternalControl(controlHolder,
                rowConstructor);
        if (menu != null) {
            newControl.setMenu(menu);
        }
        fireRowConstructionEvent(newControl);
        TableRow newRow = new TableRow(this, newControl);
        if (newRow.getRowControl() instanceof Composite) {
            Composite rowComp = (Composite) newRow.getRowControl();
            if (rowComp.getLayout() instanceof GridRowLayout) {
                rowComp.setBackground(getBackground());
                rowComp.addPaintListener(rowPaintListener);
            }
        }
        return newRow;
    }

    // Property getters/setters
    // --------------------------------------------------------------

	/*
	 * These are internal API. <p> Plese refer to the JavaDoc on CompositeTable
	 * for detailed description of these property methods.
	 */

    /**
     * (non-API) Method getHeaderControl. Return the prototype control being
     * used as a header.
     *
     * @return The header control
     */
    public Control getHeaderControl() {
        return headerControl;
    }

    /**
     * Returns the actual header control (not the prototype).
     *
     * @return a control instance or null, if no header is available
     */
    Control getHeader() {
        return myHeader;
    }

    /**
     * Method setMaxRowsVisible. Sets the maximum number of rows that will be
     * permitted in the table at once. For example, setting this property to 1
     * will have the effect of creating a single editing area with a scroll bar
     * on the right allowing the user to scroll through all rows using either
     * the mouse or the PgUp/PgDn keys. The default value is Integer.MAX_VALUE.
     *
     * @param maxRowsVisible the maximum number of rows that are permitted to be visible at
     *                       one time, regardless of the control's size.
     */
    public void setMaxRowsVisible(int maxRowsVisible) {
        this.maxRowsVisible = maxRowsVisible;
        updateVisibleRows();
    }

    /**
     * Method getNumRowsVisible. Returns the actual number of rows that are
     * currently visible. Normally CompositeTable displays as many rows as will
     * fit vertically given the control's size. This value can be clamped to a
     * maximum using the MaxRowsVisible property.
     *
     * @return the actual number of rows that are currently visible.
     */
    public int getNumRowsVisible() {
        return rows.size();
    }

    /**
     * Method setNumRowsInCollection. Sets the number of rows in the data
     * structure that is being edited.
     *
     * @param numRowsInCollection the number of rows represented by the underlying data
     *                            structure.
     */
    public void setNumRowsInCollection(int numRowsInCollection) {
        this.topRow = 0;
        if (topRow + currentRow > numRowsInCollection) {
            if (topRow < numRowsInCollection) {
                currentRow = numRowsInCollection - topRow;
            } else {
                topRow = numRowsInCollection - 1;
                currentRow = 0;
            }
            deferredSetFocus(getCurrentRowControl(), false);
        }
        this.numRowsInCollection = numRowsInCollection;
        updateVisibleRows();
        refreshAllRows();
    }

    private void doSetTopRow(int topRow, int currentRow) {
//      fireRowDepartEvent();
        this.topRow = topRow;
        this.currentRow = currentRow;
        updateVisibleRows();
//      fireRowArriveEvent();
    }

    /**
     * Method setTopRow. Set the number of the line that is being displayed in
     * the top row of the CompositeTable editor (0-based). If the new top row is
     * not equal to the current top row, the table will automatically be
     * scrolled to the new position. This number must be greater than 0 and less
     * than NumRowsInCollection.
     *
     * @param topRow the line number of the new top row.
     */
    public void setTopRow(int topRow) {
        fireRowDepartEvent();
        int topRowDelta = this.topRow - topRow;
        doSetTopRow(topRow, currentRow + topRowDelta);
        fireRowArriveEvent();
    }

    /**
     * Method getTopRow. Return the number of the line that is being displayed
     * in the top row of the CompositeTable editor (0-based).
     *
     * @return the number of the top line.
     */
    public int getTopRow() {
        return topRow;
    }

    /**
     * Method getSelection. Returns the currently-selected (column, row) pair
     * where the row specifies the offset from the top of the table window. In
     * order to get the current row in the underlying data structure, use
     * getSelection().y + getCurrentRow().
     *
     * @return the currently-selected (column, row) pair where the row specifies
     * the offset from the top of the table window, or null if no
     * selection is available.
     */
    public Point getSelection() {
        return currentRow != -1 ? new Point(currentColumn, currentRow) : null;
    }

    /**
     * Method setSelection. Sets the currently-selected (column, row) pair where
     * the row specifies the offset from the top of the table window. In order
     * to get the current row in the underlying data structure, use
     * getSelection().y + getCurrentRow().
     *
     * @param column the column to select
     * @param row    the row to select
     */
    public void setSelection(int column, int row) {
        int topRowDelta = computeTopRowDelta(row);
        if (topRowDelta != 0) {
            doSetTopRow(topRow + topRowDelta, currentRow);
            row += -1 * topRowDelta;
            internalSetSelection(column, row, true);
        } else {
            if (row == currentRow)
                internalSetSelection(column, row, false);
            else {
                if (fireRequestRowChangeEvent())
                    internalSetSelection(column, row, true);
            }
        }
    }

    /**
     * (non-API) See {@link CompositeTable#clearSelection()} instead.
     */
    public void clearSelection() {
        Point currentSelection = getSelection();
        if (currentSelection != null) {
            fireRowDepartEvent();
            currentRow = -1;
        }
    }

    /**
     * Method setWeights. Indicates that the column weights were just set and we
     * should re-layout the control holder object.
     */
    public void setWeights() {
        layoutControlHolder();
    }

    // Refresh Event API
    // --------------------------------------------------------------------------

    /**
     * Adds the specified listener to the set of listeners that will be notified
     * when a row refresh event occurs.
     *
     * @param listener the listener to add.
     */
    public void addRefreshContentProvider(IRowContentProvider listener) {
        parent.contentProviders.add(listener);
    }

    /**
     * Remove the specified listener from the set of listeners that will be
     * notified when a row refresh event occurs.
     *
     * @param listener the listener to remove.
     */
    public void removeRefreshContentProvider(IRowContentProvider listener) {
        parent.contentProviders.remove(listener);
    }

    private void fireRefreshEvent(int positionInCollection, Control rowControl) {
        if (numRowsInCollection < 1) {
            return;
        }
        for (Iterator<IRowContentProvider> refreshListenersIter = parent.contentProviders.iterator(); refreshListenersIter
                .hasNext(); ) {
            IRowContentProvider listener = refreshListenersIter
                    .next();
            listener.refresh(parent, positionInCollection, rowControl);
        }
    }

    // Empty table placeholder
    // --------------------------------------------------------------------

    private void createEmptyTablePlaceholer() {
        emptyTablePlaceholder = new EmptyTablePlaceholder(controlHolder,
                SWT.NULL);
        if (rowControl != null)
            emptyTablePlaceholder.setBackground(rowControl.getBackground());
        emptyTablePlaceholder.setMessage(parent.getInsertHint());
    }

    private void disposeEmptyTablePlaceholder() {
        if (emptyTablePlaceholder != null) {
            emptyTablePlaceholder.dispose();
            emptyTablePlaceholder = null;
        }
    }

    // Event Handling
    // -----------------------------------------------------------------------------

    private boolean needToRequestRC = true;

    private Listener displayKeyDownFilter = new Listener() {
        public void handleEvent(Event event) {
            if (rowControl.getClass().isAssignableFrom(event.widget.getClass())) {
                doMakeFocusedRowVisible();
            }
        }
    };


    /**
     * Handle a keyPressed event on any row control.
     *
     * @param sender The row that is sending the event
     * @param e      the actual KeyEvent
     */
    public void keyPressed(TableRow sender, KeyEvent e) {
        if (doMakeFocusedRowVisible()) return;

        if ((e.stateMask & SWT.CONTROL) != 0) {
            switch (e.keyCode) {
                case SWT.HOME:
                    doFocusInitialRow();
                    return;
                case SWT.END:
                    doFocusLastRow();
                    return;
                case SWT.INSERT:
                    doInsertRow();
                    return;
                case SWT.DEL:
                    doDeleteRow();
                    return;
                default:
                    return;
            }
        }
        switch (e.keyCode) {
            case SWT.ARROW_UP:
                doRowUp();
                return;
            case SWT.ARROW_DOWN:
                doRowDown();
                return;
            case SWT.PAGE_UP:
                doPageUp();
                return;
            case SWT.PAGE_DOWN:
                doPageDown();
                return;
        }
    }

    /**
     * Handle the keyTraversed event on any child control in the table.
     *
     * @param sender The row sending the event.
     * @param e      The SWT TraverseEvent
     */
    public void keyTraversed(TableRow sender, TraverseEvent e) {
        if (doMakeFocusedRowVisible()) return;

        if (parent.isTraverseOnTabsEnabled()) {
            if (e.detail == SWT.TRAVERSE_TAB_NEXT) {
                if (currentColumn >= sender.getNumColumns() - 1) {
                    e.detail = SWT.TRAVERSE_NONE;
                    handleNextRowNavigation();
                }
            } else if (e.detail == SWT.TRAVERSE_TAB_PREVIOUS) {
                if (currentColumn == 0) {
                    e.detail = SWT.TRAVERSE_NONE;
                    handlePreviousRowNavigation(sender);
                }
            } else if (e.detail == SWT.TRAVERSE_RETURN) {
                e.detail = SWT.TRAVERSE_NONE;
                if (currentColumn >= sender.getNumColumns() - 1) {
                    handleNextRowNavigation();
                } else {
                    deferredSetFocus(getControl(currentColumn + 1, currentRow),
                            false);
                }
            }
        } else {
            if (e.detail == SWT.TRAVERSE_TAB_NEXT) {
                if (currentColumn >= sender.getNumColumns() - 1) {
                    e.detail = SWT.TRAVERSE_NONE;
                }
            } else if (e.detail == SWT.TRAVERSE_TAB_PREVIOUS) {
                if (currentColumn == 0) {
                    e.detail = SWT.TRAVERSE_NONE;
                }
            }
        }
    }

    /**
     * Makes sure that the focused row is visible
     *
     * @return true if the display needed to be scrolled; false otherwise
     */
    public boolean doMakeFocusedRowVisible() {
        if (numRowsVisible < 1) {
            return false;
        }
        int topRowDelta = computeTopRowDelta(currentRow);
        if (topRowDelta == 0) {
            return false;
        }
//		currentRow += -1 * topRowDelta;

        doSetTopRow(topRow + topRowDelta, currentRow + (-1 * topRowDelta));
        Control control = getControl(currentColumn, currentRow);
        if (control != null) {
            control.setFocus(); // ?? Can I get away with avoiding asyncExec here ??
        }
        return true;
    }

    private int computeTopRowDelta(int row) {
        int topRowDelta;
        if (row < 0) {
            topRowDelta = row;
        } else if (row >= getNumRowsVisible()) {
            topRowDelta = row - getNumRowsVisible() + 1;
        } else {
            return 0;
        }
        return topRowDelta;
    }

    /**
     * The SelectionListener for the table's vertical slider control.
     */
    private SelectionListener sliderSelectionListener = new SelectionListener() {
        public void widgetSelected(SelectionEvent e) {
            if (vSlider.getSelection() == topRow) {
                return;
            }

            if (!fireRequestRowChangeEvent()) {
                vSlider.setSelection(topRow);
                return;
            }

            deselectCurrentRowIfVisible();

            int delta = topRow - vSlider.getSelection();
            int oldCurrentRow = currentRow;
            currentRow = vSlider.getSelection();

//			setTopRow(vSlider.getSelection());  // Removed as a result of patch
            doSetTopRow(vSlider.getSelection(), currentRow + delta);

            // If the focused row just became visible, show the focus
            if (oldCurrentRow < 0 || oldCurrentRow >= getNumRowsVisible()) {
                if (currentRow >= 0 && currentRow < getNumRowsVisible()) {
                    Control newFocusControl = getControl(currentColumn, currentRow);
                    if (newFocusControl == null) {
                        return;
                    }
                    if (newFocusControl.isFocusControl()) {
                        newFocusControl.notifyListeners(SWT.FocusIn, new Event());
                    } else {
                        deferredSetFocus(newFocusControl, true);
                    }
                }
            } else {
                // If the new viewport doesn't overlap the old one, hide the focus
                if (currentRow < 0 || currentRow >= getNumRowsVisible()) {
                    //					deleteRowAt(oldCurrentRow);
//					getControl(currentColumn, oldCurrentRow).getParent().setVisible(false);
//					getControl(currentColumn, oldCurrentRow).getParent().setVisible(true);
                    Control control = getControl(currentColumn, oldCurrentRow);
                    if (control != null) {
                        control.notifyListeners(SWT.FocusOut, new Event());
                    }
                }
            }
        }

        public void widgetDefaultSelected(SelectionEvent e) {
            widgetSelected(e);
        }
    };

    private SelectionListener hSliderSelectionListener = new SelectionListener() {
        /* (non-Javadoc)
         * @see org.eclipse.swt.events.SelectionListener#widgetSelected(org.eclipse.swt.events.SelectionEvent)
         */
        public void widgetSelected(SelectionEvent e) {
            Point scrollerSize = hScroller.getSize();
            int preferredWidth = controlHolder.computeSize(SWT.DEFAULT,
                    SWT.DEFAULT, true).x;
            Rectangle controlHolderBounds =
                    new Rectangle(-1 * hSlider.getSelection(), 0, preferredWidth,
                            scrollerSize.y);
            controlHolder.setBounds(controlHolderBounds);
        }

        public void widgetDefaultSelected(SelectionEvent e) {
            widgetSelected(e);
        }
    };

    /**
     * Scroll wheel event handling.
     */
    public void handleEvent(Event event) {
        event.doit = false;
        if (event.count > 0) { // scroll up
            if (topRow > 0) {
                if (!fireRequestRowChangeEvent()) {
                    return;
                }
                deselectCurrentRowIfVisible();
                doSetTopRow(topRow - 1, currentRow + 1);
                if (isRowVisible(currentRow)) {
                    deferredSetFocus(getControl(currentColumn, currentRow), true);
                }
            }
        } else { // scroll down
            if (topRow < numRowsInCollection - numRowsVisible) {
                if (!fireRequestRowChangeEvent()) {
                    return;
                }
                deselectCurrentRowIfVisible();
                doSetTopRow(topRow + 1, currentRow - 1);
                if (isRowVisible(currentRow)) {
                    deferredSetFocus(getControl(currentColumn, currentRow), true);
                }
            }
        }
//		if (event.count > 0) {    // Old code (before patch)
//			if (topRow > 0) {
//				if (!fireRequestRowChangeEvent()) {
//					return;
//				}
//				deselectCurrentRowIfVisible();
//				setTopRow(topRow - 1);
//				++currentRow;
//				if (currentRow == 0) {
//					deferredSetFocus(getControl(currentColumn, currentRow), true);
//				}
//			}
//		} else {
//			if (topRow < numRowsInCollection - numRowsVisible) {
//				if (!fireRequestRowChangeEvent()) {
//					return;
//				}
//				deselectCurrentRowIfVisible();
//				setTopRow(topRow + 1);
//				--currentRow;
//				if (currentRow == getNumRowsVisible()-1) {
//					deferredSetFocus(getControl(currentColumn, currentRow), true);
//				}
//			}
//		}
    }

    private void deselectCurrentRowIfVisible() {
        if (currentRow >= 0 && currentRow < numRowsVisible) {
            Control control = getControl(currentColumn, currentRow);
            if (control != null) {
                deselect(control);
            }
        }
    }

    /**
     * Handle focusLost events on any child control. This is not currently used.
     *
     * @param sender The row containing the sending control.
     * @param e      The SWT FocusEvent.
     */
    public void focusLost(TableRow sender, FocusEvent e) {
    }

    /**
     * Handle focusGained events on any child control.
     * <p>
     * FIXME: Needs to automatically scroll horizontally if the newly-focused
     * control is fully or partially occluded.
     *
     * @param sender The row containing the sending control.
     * @param e      The SWT FocusEvent.
     */
    public void focusGained(TableRow sender, FocusEvent e) {
        boolean rowChanged = false;
        int senderRowNumber = getRowNumber(sender);
        if (senderRowNumber != currentRow) {
            if (needToRequestRC) {
                if (!fireRequestRowChangeEvent()) {
                    // Go back if we're not allowed to be here
                    deferredSetFocus(getControl(currentColumn, currentRow),
                            false);
                }
            } else {
                needToRequestRC = true;
            }
            rowChanged = true;
        }

        currentRow = senderRowNumber;
        currentColumn = sender.getColumnNumber((Control) e.widget);

        if (rowChanged)
            fireRowArriveEvent();
    }

    private PaintListener headerPaintListener = new PaintListener() {
        public void paintControl(PaintEvent e) {
            if (parent.linesVisible) {
                drawGridLines(e, true);
            }
        }
    };

    private PaintListener rowPaintListener = new PaintListener() {
        public void paintControl(PaintEvent e) {
            if (parent.linesVisible) {
                drawGridLines(e, false);
            }
        }
    };

    private void drawGridLines(PaintEvent e, boolean isHeader) {
        Color oldColor = e.gc.getForeground();
        try {
            // Get the colors we need
            Display display = Display.getCurrent();
            Color lineColor = display
                    .getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW);
            Color secondaryColor = display
                    .getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
            Color hilightColor = display
                    .getSystemColor(SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW);
            if (!isHeader) {
                lineColor = display
                        .getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW);
            }

            // Get the control
            Control toPaint = (Control) e.widget;
            Point controlSize = toPaint.getSize();

            // Draw the bottom line(s)
            e.gc.setForeground(lineColor);
            e.gc.drawLine(0, controlSize.y - 1, controlSize.x,
                    controlSize.y - 1);
            if (isHeader) {
                e.gc.setForeground(secondaryColor);
                e.gc.drawLine(0, controlSize.y - 2, controlSize.x,
                        controlSize.y - 2);
                e.gc.setForeground(hilightColor);
                e.gc.drawLine(0, 1, controlSize.x, 1);
            }

            // Now draw lines around the child controls, if there are any
            if (toPaint instanceof Composite) {
                Composite row = (Composite) toPaint;
                Control[] children = row.getChildren();
                for (int i = 0; i < children.length; i++) {
                    Rectangle childBounds = children[i].getBounds();

                    // Paint the beginning lines
                    if (isHeader) {
                        e.gc.setForeground(hilightColor);
                        e.gc.drawLine(childBounds.x - 2, 1, childBounds.x - 2,
                                controlSize.y - 2);
                    }

                    // Paint the ending lines
                    e.gc.setForeground(lineColor);
                    int lineLeft = childBounds.x + childBounds.width + 1;
                    e.gc.drawLine(lineLeft, 0, lineLeft, controlSize.y);
                    if (isHeader) {
                        e.gc.setForeground(secondaryColor);
                        e.gc.drawLine(lineLeft - 1, 0, lineLeft - 1,
                                controlSize.y - 1);
                    }
                }
            }
        } finally {
            e.gc.setForeground(oldColor);
        }
    }

    // Event Firing
    // -------------------------------------------------------------------------------

    /**
     * Fire the row construction event
     *
     * @param newControl The new row's SWT control
     */
    private void fireRowConstructionEvent(Control newControl) {
        for (Iterator<RowConstructionListener> rowConstructionListenersIter = parent.rowConstructionListeners
                .iterator(); rowConstructionListenersIter.hasNext(); ) {
            RowConstructionListener listener = rowConstructionListenersIter
                    .next();
            listener.rowConstructed(newControl);
        }
    }

    /**
     * Fire the row construction event
     *
     * @param newControl The new row's SWT control
     */
    private void fireHeaderConstructionEvent(Control newControl) {
        for (Iterator<RowConstructionListener> rowConstructionListenersIter = parent.rowConstructionListeners
                .iterator(); rowConstructionListenersIter.hasNext(); ) {
            RowConstructionListener listener = rowConstructionListenersIter
                    .next();
            listener.headerConstructed(newControl);
        }
    }

    /**
     * Indicate to listeners that the focus is arriving on the specified row
     */
    private void fireRowArriveEvent() {
        if (rows.size() < 1 || !isRowVisible(currentRow)) {
            return;
        }
        for (Iterator<IRowFocusListener> rowChangeListenersIter = parent.rowFocusListeners
                .iterator(); rowChangeListenersIter.hasNext(); ) {
            IRowFocusListener listener =
                    rowChangeListenersIter.next();
            // currentRow() can be null if it's scrolled off the top or bottom
            TableRow row = currentRow();
            Control control = row != null ? row.getRowControl() : null;
            listener.arrive(parent, topRow + currentRow, control);
        }

    }

    /**
     * Request permission from all listeners to leave the current row.
     *
     * @return true if all listeners permit the row change; false otherwise.
     */
    private boolean fireRequestRowChangeEvent() {
        if (rows.size() < 1 || !isRowVisible(currentRow)) {
            return true;
        }
        if (currentRow > rows.size() - 1) {
            // (if the other row is already gone)
            return true;
        }
        for (Iterator<IRowFocusListener> rowChangeListenersIter = parent.rowFocusListeners
                .iterator(); rowChangeListenersIter.hasNext(); ) {
            IRowFocusListener listener = rowChangeListenersIter
                    .next();
            // currentRow() can be null if it's scrolled off the top or bottom
            TableRow row = currentRow();
            Control control = row != null ? row.getRowControl() : null;
            if (!listener.requestRowChange(parent, topRow + currentRow,
                    control)) {
                return false;
            }
        }
        fireRowDepartEvent();
        return true;
    }

    /**
     * Indicate to listeners that the focus is about to leave the current row.
     */
    private void fireRowDepartEvent() {
        if (rows.size() < 1 || !isRowVisible(currentRow)) {
            return;
        }
        for (Iterator<IRowFocusListener> rowChangeListenersIter = parent.rowFocusListeners
                .iterator(); rowChangeListenersIter.hasNext(); ) {
            IRowFocusListener listener = rowChangeListenersIter
                    .next();
            // currentRow() can be null if it's scrolled off the top or bottom
            TableRow row = currentRow();
            Control control = row != null ? row.getRowControl() : null;
            if (control != null)
                listener.depart(parent, topRow + currentRow, control);
        }
    }

    /**
     * Request deletion of the current row from the underlying data structure.
     *
     * @return true if the deletion was successful; false otherwise.
     */
    private boolean fireDeleteEvent() {
        if (parent.deleteHandlers.size() < 1) {
            return false;
        }

        int absoluteRow = topRow + currentRow;
        for (Iterator<IDeleteHandler> deleteHandlersIter = parent.deleteHandlers.iterator(); deleteHandlersIter
                .hasNext(); ) {
            IDeleteHandler handler = deleteHandlersIter.next();
            if (!handler.canDelete(absoluteRow)) {
                return false;
            }
        }
        for (Iterator<IDeleteHandler> deleteHandlersIter = parent.deleteHandlers.iterator(); deleteHandlersIter
                .hasNext(); ) {
            IDeleteHandler handler = deleteHandlersIter.next();
            handler.deleteRow(absoluteRow);
        }
        return true;
    }

    private void fireRowDeletedEvent() {
        int absoluteRow = topRow + currentRow;
        for (Iterator<IDeleteHandler> deleteHandlersIter = parent.deleteHandlers.iterator(); deleteHandlersIter
                .hasNext(); ) {
            IDeleteHandler handler = deleteHandlersIter.next();
            handler.rowDeleted(absoluteRow);
        }
    }

    /**
     * Request that the model insert a new row into itself.
     *
     * @return The 0-based offset of the new row from the start of the
     * collection or -1 if a new row could not be inserted.
     */
    private int fireInsertEvent() {
        if (parent.insertHandlers.size() < 1) {
            return -1;
        }

        for (Iterator<IInsertHandler> insertHandlersIter = parent.insertHandlers.iterator(); insertHandlersIter
                .hasNext(); ) {
            IInsertHandler handler = insertHandlersIter.next();
            int resultRow = handler.insert(topRow + currentRow);
            if (resultRow >= 0) {
                return resultRow;
            }
        }

        return -1;
    }

    /**
     * Tell listeners that we just scrolled.
     *
     * @param scrollEvent TODO
     */
    private void fireScrollEvent(ScrollEvent scrollEvent) {
        if (parent.scrollListeners.size() < 1) {
            return;
        }

        for (Iterator<ScrollListener> scrollListenersIter = parent.scrollListeners.iterator(); scrollListenersIter.hasNext(); ) {
            ScrollListener scrollListener = scrollListenersIter.next();
            scrollListener.tableScrolled(scrollEvent);
        }
    }

    // Event Handling, utility methods
    // ------------------------------------------------------------

    /**
     * Set the widget's selection to an empty selection.
     *
     * @param widget The widget to deselect
     */
    private void deselect(Widget widget) {
        if (DuckType.instanceOf(ISelectableRegionControl.class, widget)) {
            ISelectableRegionControl control = (ISelectableRegionControl) DuckType
                    .implement(ISelectableRegionControl.class, widget);
            control.setSelection(0, 0);
        }
    }

    /**
     * Try to go to the next row in the collection.
     */
    private void handleNextRowNavigation() {
        if (currentRow < numRowsVisible - 1) {
            if (!fireRequestRowChangeEvent()) {
                return;
            }
            needToRequestRC = false;

            deselect(getControl(currentColumn, currentRow));

            deferredSetFocus(getControl(0, currentRow + 1), false);
        } else {
            if (topRow + numRowsVisible >= numRowsInCollection) {
                // We're at the end; don't go anywhere
                return;
            }
            // We have to scroll forwards
            if (!fireRequestRowChangeEvent()) {
                return;
            }
            needToRequestRC = false;

            deselect(getControl(currentColumn, currentRow));

            doSetTopRow(topRow + 1, currentRow);
            deferredSetFocus(getControl(0, currentRow), true);
        }
    }

    /**
     * Try to go to the previous row in the collection.
     *
     * @param row The current table row.
     */
    private void handlePreviousRowNavigation(TableRow row) {
        if (currentRow == 0) {
            if (topRow == 0) {
                // We're at the beginning of the table; don't go anywhere
                return;
            }
            // We have to scroll backwards
            if (!fireRequestRowChangeEvent()) {
                return;
            }
            needToRequestRC = false;

            deselect(getControl(currentColumn, currentRow));

            doSetTopRow(topRow - 1, currentRow);
            deferredSetFocus(getControl(row.getNumColumns() - 1, 0), true);
        } else {
            if (!fireRequestRowChangeEvent()) {
                return;
            }
            needToRequestRC = false;

            deselect(getControl(currentColumn, currentRow));

            deferredSetFocus(
                    getControl(row.getNumColumns() - 1, currentRow - 1), false);
        }
    }

    /**
     * Gets the current TableRow.
     *
     * @return the current TableRow
     */
    private TableRow currentRow() {
        if (currentRow < 0 || currentRow > rows.size() - 1) {
            return null;
        }
        return rows.get(currentRow);
    }

    /**
     * Returns the SWT control corresponding to the current row.
     *
     * @return the current row control.
     */
    public Control getCurrentRowControl() {
        TableRow currentRow = currentRow();
        if (currentRow == null) {
            return null;
        }
        return currentRow().getRowControl();
    }

    /**
     * Method getRowControls. Returns an array of SWT controls where each
     * control represents a row control in the CompositeTable's current scrolled
     * position. If CompositeTable is resized, scrolled, such that the rows that
     * the CompositeTable control is displaying change in any way, the array
     * that is returned by this method will become out of date and need to be
     * retrieved again.
     *
     * @return Control[] An array of SWT Control objects, each representing an
     * SWT row object.
     */
    public Control[] getRowControls() {
        Control[] rowControls = new Control[rows.size()];
        for (int i = 0; i < rowControls.length; i++) {
            rowControls[i] = getRowByNumber(i).getRowControl();
        }
        return rowControls;
    }

    private Menu menu = null;

    /* (non-Javadoc)
     * @see org.eclipse.swt.widgets.Control#setMenu(org.eclipse.swt.widgets.Menu)
     */
    public void setMenu(final Menu menu) {
        this.menu = menu;
        setMenuOnCollection(rows, menu);
        setMenuOnCollection(spareRows, menu);
    }

    private void setMenuOnCollection(LinkedList<TableRow> collection, Menu menu) {
        for (Iterator<TableRow> rowsIter = collection.iterator(); rowsIter.hasNext(); ) {
            TableRow row = rowsIter.next();
            row.getRowControl().setMenu(menu);
        }
    }


    /**
     * Method getControlRow.  Given a row control, returns its row number
     * relative to the topRow.
     *
     * @param rowControl The row object to find
     * @return The row number of the rowControl relative to the topRow (0-based)
     * @throws IllegalArgumentException if rowControl is not currently visible
     */
    public int getControlRow(Control rowControl) {
        for (int row = 0; row < rows.size(); row++) {
            if (getRowByNumber(row).getRowControl() == rowControl) {
                return row;
            }
        }
        throw new IllegalArgumentException("getControlRow passed a control that is not visible inside CompositeTable");
    }

    /**
     * Method getControlRowObject.  Given a row control, returns its row number
     * relative to the topRow.
     *
     * @param rowControl The row object to find
     * @return The row object managing the rowControl
     * @throws IllegalArgumentException if rowControl is not currently visible
     */
    public TableRow getControlRowObject(Control rowControl) {
        for (Iterator<TableRow> rowsIter = rows.iterator(); rowsIter.hasNext(); ) {
            TableRow row = rowsIter.next();
            if (row.getRowControl() == rowControl) {
                return row;
            }
        }
        throw new IllegalArgumentException("getControlRowObject passed a control that is not visible inside CompositeTable");
    }

    /**
     * Returns the TableRow by the specified 0-based offset from the top visible
     * row.
     *
     * @param rowNumber 0-based offset of the requested fow starting from the top
     *                  visible row.
     * @return The corresponding TableRow or null if there is none.
     */
    private TableRow getRowByNumber(int rowNumber) {
        if (rowNumber > rows.size() - 1 || rowNumber < 0) {
            return null;
        }
        return rows.get(rowNumber);
    }

    /**
     * Return the SWT control at (column, row), where row is a 0-based number
     * starting from the top visible row.
     *
     * @param column the 0-based column.
     * @param row    the 0-based row starting from the top visible row.
     * @return the SWT control at (column, row)
     */
    private Control getControl(int column, int row) {
        TableRow rowObject = getRowByNumber(row);
        if (rowObject == null) {
            throw new IndexOutOfBoundsException("Request for a nonexistent row"); //$NON-NLS-1$
        }
        Control result = rowObject.getColumnControl(column);
        return result;
    }

    /**
     * Return the 0-based row number corresponding to a particular TableRow
     * object.
     *
     * @param row The TableRow to translate to row coordinates.
     * @return the 0-based row number or -1 if the specified TableRow is not
     * visible.
     */
    private int getRowNumber(TableRow row) {
        int rowNumber = 0;
        for (Iterator<TableRow> rowIter = rows.iterator(); rowIter.hasNext(); ) {
            TableRow candidate = rowIter.next();
            if (candidate == row) {
                return rowNumber;
            }
            ++rowNumber;
        }
        return -1;
    }

    /**
     * Set the focus to the specified (column, row). If rowChange is true, fire
     * a row change event, otherwise be silent.
     *
     * @param column    The 0-based column to focus
     * @param row       The 0-based row to focus
     * @param rowChange true if a row change event should be fired; false otherwise.
     */
    private void internalSetSelection(int column, int row, boolean rowChange) {
        Control toFocus = getControl(column, row);
        if (toFocus == null) {
            return;
        }
        if (toFocus.isFocusControl()) {
            toFocus.notifyListeners(SWT.FocusIn, new Event());
        } else {
            deferredSetFocus(toFocus, rowChange);
        }
    }

    /**
     * Set the focus to the specified control after allowing all pending events
     * to complete first. If rowChange is true, fire a row arrive event after
     * the focus has been set.
     *
     * @param toFocus   The SWT Control to focus
     * @param rowChange true if the rowArrive event should be fired; false otherwise.
     */
    private void deferredSetFocus(final Control toFocus, final boolean rowChange) {
        if (toFocus == null) {
            return;
        }
        Display.getCurrent().asyncExec(new Runnable() {
            public void run() {
                if (toFocus.isDisposed()) return;
                toFocus.setFocus();
                if (rowChange) {
                    fireRowArriveEvent();
                }
            }
        });
    }

    public void doFocusInitialRow() {
        if (topRow <= 0) {
            return;
        }

        if (!fireRequestRowChangeEvent()) {
            return;
        }
        needToRequestRC = false;

        Widget widget = getDisplay().getFocusControl();
        deselect(widget);  // Used to be e.widget

        // If the focus is already in the top visible row, we will need
        // to explicitly
        // fire an arrive event.
        boolean needToArrive = true;
        if (currentRow > 0) {
            needToArrive = false;
        }

        doSetTopRow(0, currentRow);

        if (needToArrive) {
            internalSetSelection(currentColumn, 0, true);
        } else {
            internalSetSelection(currentColumn, 0, false);
        }
    }

    public void doFocusLastRow() {
        if (topRow + numRowsVisible < numRowsInCollection) {
            if (!fireRequestRowChangeEvent()) {
                return;
            }
            needToRequestRC = false;

            Widget widget = getDisplay().getFocusControl();
            deselect(widget);  // Used to be e.widget

            // If the focus is already in the last visible row, we will
            // need to explicitly
            // fire an arrive event.
            boolean needToArrive = true;
            if (currentRow < numRowsVisible - 1) {
                needToArrive = false;
            }

            doSetTopRow(numRowsInCollection - numRowsVisible, currentRow);

            if (needToArrive) {
                internalSetSelection(currentColumn, numRowsVisible - 1,
                        true);
            } else {
                internalSetSelection(currentColumn, numRowsVisible - 1,
                        false);
            }
        }
    }

    public void doPageUp() {
        if (topRow > 0) {
            if (!fireRequestRowChangeEvent()) {
                return;
            }
            needToRequestRC = false;

            int newTopRow = topRow - numRowsInDisplay;
            if (newTopRow < 0) {
                newTopRow = 0;
            }
            doSetTopRow(newTopRow, 0);
            internalSetSelection(currentColumn, currentRow, true);
        }
    }

    public void doPageDown() {
        if (topRow + numRowsVisible < numRowsInCollection) {
            if (!fireRequestRowChangeEvent()) {
                return;
            }
            needToRequestRC = false;

            int newTopRow = topRow + numRowsVisible;
            if (newTopRow >= numRowsInCollection - 1) {
                newTopRow = numRowsInCollection - 1;
            }
            doSetTopRow(newTopRow, numRowsVisible - 1);
            internalSetSelection(currentColumn, currentRow, true);
        }
    }

    public void doRowUp() {
        if (maxRowsVisible <= 1)
            return;

        if (currentRow > 0) {
            if (!fireRequestRowChangeEvent()) {
                return;
            }
            needToRequestRC = false;

            Widget widget = getDisplay().getFocusControl();
            deselect(widget);  // Used to be e.widget

            internalSetSelection(currentColumn, currentRow - 1, false);
            return;
        }
        if (topRow > 0) {
            if (!fireRequestRowChangeEvent()) {
                return;
            }
            needToRequestRC = false;

            Widget widget = getDisplay().getFocusControl();
            deselect(widget);  // Used to be e.widget

            doSetTopRow(topRow - 1, currentRow);
            internalSetSelection(currentColumn, currentRow, true);
            return;
        }
    }

    public void doRowDown() {
        if (maxRowsVisible <= 1)
            return;

        if (currentRow < numRowsVisible - 1) {
            if (!fireRequestRowChangeEvent()) {
                return;
            }
            needToRequestRC = false;

            Widget widget = getDisplay().getFocusControl();
            deselect(widget);  // Used to be e.widget

            internalSetSelection(currentColumn, currentRow + 1, false);
            return;
        }
        if (topRow + numRowsVisible < numRowsInCollection) {
            if (!fireRequestRowChangeEvent()) {
                return;
            }
            needToRequestRC = false;

            Widget widget = getDisplay().getFocusControl();
            deselect(widget);  // Used to be e.widget

            doSetTopRow(topRow + 1, currentRow);
            internalSetSelection(currentColumn, currentRow, true);
            return;
        }
    }

    public boolean doInsertRow() {
        // If no insertHandler has been registered, bail out
        if (parent.insertHandlers.size() < 1) {
            return false;
        }

        // Make sure we can leave the current row
        if (!fireRequestRowChangeEvent()) {
            return false;
        }
        needToRequestRC = false;

        // Insert the new object
        int newRowPosition = fireInsertEvent();
        if (newRowPosition < 0) {
            // This should never happen, but...
            throw new IllegalArgumentException("Insert < row 0???");
        }

        disposeEmptyTablePlaceholder();

        // If the current widget has a selection, deselect it
        Widget widget = getDisplay().getFocusControl();
        deselect(widget);  // Used to be e.widget

        // If the new row is in the visible space, refresh it
        if (topRow <= newRowPosition
                && numRowsVisible > newRowPosition - topRow) {
            insertRowAt(newRowPosition - topRow);
            ++numRowsInCollection;
            updateVisibleRows();
            int newRowNumber = newRowPosition - topRow;
            if (newRowNumber != currentRow) {
                internalSetSelection(currentColumn, newRowNumber, false);
            } else {
                internalSetSelection(currentColumn, newRowNumber, true);
            }
            return true;
        }

        // else...

        ++numRowsInCollection;

        // If the new row is above us, scroll up to it
        if (newRowPosition < topRow + currentRow) {
            doSetTopRow(newRowPosition, currentRow);
            Display.getDefault().asyncExec(new Runnable() {
                public void run() {
                    updateVisibleRows();
                    if (currentRow != 0) {
                        internalSetSelection(currentColumn, 0, false);
                    } else {
                        internalSetSelection(currentColumn, 0, true);
                    }
                }
            });
        } else {
            // If we're appending
            if (numRowsInDisplay > numRowsVisible) {
                updateVisibleRows();
                int newRowNumber = newRowPosition - topRow;
                if (newRowNumber != currentRow) {
                    internalSetSelection(currentColumn, newRowNumber,
                            false);
                } else {
                    internalSetSelection(currentColumn, newRowNumber,
                            true);
                }
            } else {
                // It's somewhere in the middle below us; scroll down to
                // it
                doSetTopRow(newRowPosition - numRowsVisible + 1, currentRow);
                int newRowNumber = numRowsVisible - 1;
                if (newRowNumber != currentRow) {
                    internalSetSelection(currentColumn, newRowNumber,
                            false);
                } else {
                    internalSetSelection(currentColumn, newRowNumber,
                            true);
                }
            }
        }
        return false;
    }

    public boolean doDeleteRow() {
        if (fireDeleteEvent()) {
            // We know the object is gone if we made it here, so now
            // refresh the display...
            --numRowsInCollection;

            // If we deleted the last row in the list
            if (currentRow >= numRowsVisible - 1) {
                // If that wasn't the last row in the collection, move
                // the focus
                if (numRowsInCollection > 0) {

                    // If we're only displaying one row, scroll first
                    if (currentRow < 1) {
                        needToRequestRC = false;
                        deleteRowAt(currentRow);
                        doSetTopRow(topRow - 1, currentRow);
                        internalSetSelection(currentColumn, currentRow,
                                true);
                    } else {
                        needToRequestRC = false;
                        internalSetSelection(currentColumn,
                                currentRow - 1, false);
                        Display.getCurrent().asyncExec(new Runnable() {
                            public void run() {
                                deleteRowAt(currentRow + 1);
                                updateVisibleRows();
                            }
                        });
                    }
                } else {
                    // Otherwise, show the placeholder object and give
                    // it focus
                    deleteRowAt(currentRow);
                    --numRowsVisible;
                    createEmptyTablePlaceholer();
                    emptyTablePlaceholder.setFocus();
                }
            } else {
                // else, keep the focus where it was
                deleteRowAt(currentRow);
                updateVisibleRows();
                internalSetSelection(currentColumn, currentRow, true);
            }
            fireRowDeletedEvent();
        }
        return false;
    }


}