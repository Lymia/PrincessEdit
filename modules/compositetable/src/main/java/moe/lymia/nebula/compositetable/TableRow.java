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
 */
package moe.lymia.nebula.compositetable;

import org.eclipse.swt.events.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

/**
 * (non-API)
 * Class TableRow.  Encapsulates operations on a SWT row control.  Discovers the
 * SWT controls inside the row control representing columns and exposes those for
 * operations by the CompositeTable.  Listens to SWT events on the column controls
 * and forwards them back to the table control for processing.
 *
 * @author djo
 */
class TableRow {
    private Control row;
    private Control[] columns;
    protected InternalCompositeTable parent;

    /**
     * Constructor TableRow.  Construct a TableRow object.
     *
     * @param parent The table containing this row.
     * @param row    The SWT control implementing this row.
     */
    public TableRow(InternalCompositeTable parent, Control row) {
        this.parent = parent;
        this.row = row;
        if (row instanceof Composite) {
            Composite rowComposite = (Composite) row;
            columns = rowComposite.getTabList();
        } else {
            columns = new Control[]{row};
        }

        recursiveAddListeners(row);
    }

    /**
     * Remove all listeners from each control.
     */
    public void dispose() {
        recursiveRemoveListeners(row);
    }

    /**
     * Recursively calls addListeners(c) for c and all its descendants.
     *
     * @param c
     */
    private void recursiveAddListeners(Control c) {
        addListeners(c);
        if (c instanceof Composite) {
            Control[] children = ((Composite) c).getChildren();
            for (int i = 0; i < children.length; i++) {
                recursiveAddListeners(children[i]);
            }
        }
    }

    /**
     * Recursively calls removeListeners(c) for c and all its descendants.
     *
     * @param c
     */
    private void recursiveRemoveListeners(Control c) {
        removeListeners(c);
        if (c instanceof Composite) {
            Control[] children = ((Composite) c).getChildren();
            for (int i = 0; i < children.length; i++) {
                recursiveRemoveListeners(children[i]);
            }
        }
    }

    /**
     * Add listeners to each control.
     *
     * @param control The control to listen to.
     */
    private void addListeners(Control control) {
        control.addKeyListener(keyListener);
        control.addFocusListener(focusListener);
        control.addTraverseListener(traverseListener);
    }

    /**
     * Remove listeners from each control.
     *
     * @param control The control to no longer listen to.
     */
    private void removeListeners(Control control) {
        control.removeKeyListener(keyListener);
        control.removeFocusListener(focusListener);
        control.removeTraverseListener(traverseListener);
    }

    /**
     * Forward key presses to the parent control
     */
    private KeyListener keyListener = new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
            parent.keyPressed(TableRow.this, e);
        }
    };

    /**
     * Forward focuse events to the parent control
     */
    private FocusListener focusListener = new FocusAdapter() {
        public void focusLost(FocusEvent e) {
            parent.focusLost(TableRow.this, e);
        }

        public void focusGained(FocusEvent e) {
            parent.focusGained(TableRow.this, e);
        }
    };

    /**
     * Forward traverse events to the parent control
     */
    private TraverseListener traverseListener = new TraverseListener() {
        public void keyTraversed(TraverseEvent e) {
            /*
			 * FEATURE IN SWT: When SWT needs to resolve a mnemonic (accelerator)
			 * character, it recursively calls the traverse event down all
			 * controls in the containership hierarchy.  If e.doit is false,
			 * no control has yet matched the mnemonic, and we don't have to
			 * do anything since we don't do mnemonic matching and no mnemonic
			 * has matched.
			 */
            if (e.doit) {
                parent.keyTraversed(TableRow.this, e);
            }
        }
    };

    /**
     * Return the SWT control implementing the row's GUI.
     *
     * @return The row's SWT control
     */
    public Control getRowControl() {
        return row;
    }

    /**
     * Return the SWT control corresponding to a particular column within
     * this row.
     *
     * @param i the 0-based offset of the column to return.
     * @return The corresponding control or null if there is no control at the
     * specified position.
     */
    public Control getColumnControl(int i) {
        if (i < 0) {
            return row;
        }
        if (i < 1 && columns.length == 0) {
            return row;
        }
        if (i < columns.length) {
            return columns[i];
        }
        return null;
    }

    /**
     * Return the column number of a specified SWT control or -1 if not found.
     *
     * @param control The control to find.
     * @return control's column number or -1 if that column control is not in this row.
     */
    public int getColumnNumber(Control control) {
        for (int i = 0; i < columns.length; i++) {
            if (columns[i] == control) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Return the number of columns in this row.
     *
     * @return The number of columns in this row.
     */
    public int getNumColumns() {
        return columns.length;
    }

    /**
     * Sets the visibility of this row.
     *
     * @param visible true if the row should be visible; false otherwise.
     */
    public void setVisible(boolean visible) {
        row.setVisible(visible);
    }

    /**
     * Returns if this row is visible.
     *
     * @return true if the row is visible; false otherwise.
     */
    public boolean getVisible() {
        return row.getVisible();
    }
}