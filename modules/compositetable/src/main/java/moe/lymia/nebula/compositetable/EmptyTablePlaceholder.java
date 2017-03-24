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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

/**
 * (Non-API)
 * Class EmptyTablePlaceholder.  An SWT control that is displayed in the table when
 * there are no rows to display.  It has four purposes:
 * <p>
 * <ul>
 * <li>Prompt the user to hit <INS> to insert a new (first) row.
 * <li>Indicate if the table has focus using a dashed line around the outside.
 * <li>Actually accept focus for the table when there are no other controls to do so.
 * <li>Forward the insert key event back to the table when the user needs to insert a row.
 * </ul>
 *
 * @author djo
 */
class EmptyTablePlaceholder extends Canvas {

    private boolean focusControl = false;
    private InternalCompositeTable parentTable = null;

    private final Color RED;

    /**
     * Constructor EmptyTablePlaceholder.  Construct an EmptyTablePlaceholder control.
     *
     * @param parent The parent control
     * @param style  Style bits.  These are the same as what Canvas accepts.
     */
    public EmptyTablePlaceholder(Composite parent, int style) {
        super(parent, style);
        parentTable = (InternalCompositeTable) parent.getParent().getParent();

        parent.addControlListener(controlListener);

        addTraverseListener(traverseListener);
        addFocusListener(focusListener);
        addKeyListener(keyListener);
        addPaintListener(paintListener);
        addDisposeListener(disposeListener);

        RED = Display.getCurrent().getSystemColor(SWT.COLOR_RED);
        setBackground(getParent().getBackground());

        resize();
    }

    /**
     * Make sure we remove our listeners...
     */
    private DisposeListener disposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            removeTraverseListener(traverseListener);
            removeFocusListener(focusListener);
            removeKeyListener(keyListener);
            removePaintListener(paintListener);
            removeDisposeListener(disposeListener);

            getParent().removeControlListener(controlListener);
        }
    };

    /**
     * Handle resize events so we can redraw ourselves correctly.
     */
    private ControlListener controlListener = new ControlAdapter() {
        public void controlResized(ControlEvent e) {
            resize();
        }
    };

    /**
     * Actually resize ourself.
     */
    private void resize() {
        Point headerSize = new Point(0, 0);
        Control header = parentTable.getHeaderControl();
        if (header != null) {
            headerSize = header.getSize();
        }
        Point parentSize = getParent().getSize();

        setBounds(0, headerSize.y + 2, parentSize.x - 4, parentSize.y - headerSize.y - 6);
    }


    // The message property
    private String message = ""; //$NON-NLS-1$

    /**
     * Return the prompt message that will be displayed to the user inside this control.
     *
     * @return The message string.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Set the prompt message that will be displayed to the user inside this control.
     *
     * @param message The message to display.
     */
    public void setMessage(String message) {
        this.message = message;
        redraw();
    }

    /**
     * Paint the control.
     */
    private PaintListener paintListener = new PaintListener() {
        public void paintControl(PaintEvent e) {
            Color oldColor = e.gc.getForeground();
            int oldLineStyle = e.gc.getLineStyle();
            int oldLineWidth = e.gc.getLineWidth();
            try {
                if (focusControl) {
                    e.gc.setLineStyle(SWT.LINE_DASH);
                    e.gc.setLineWidth(2);
                    Point parentSize = getSize();
                    e.gc.drawRectangle(1, 2, parentSize.x - 2, parentSize.y - 3);
                }

                e.gc.setForeground(RED);
                e.gc.drawText(getMessage(), 3, 3);
            } finally {
                e.gc.setForeground(oldColor);
                e.gc.setLineStyle(oldLineStyle);
                e.gc.setLineWidth(oldLineWidth);
            }
        }
    };

    /**
     * When we gain/lose focus, redraw ourselves appropriately
     */
    private FocusListener focusListener = new FocusListener() {
        public void focusGained(FocusEvent e) {
            focusControl = true;
            redraw();
        }

        public void focusLost(FocusEvent e) {
            focusControl = false;
            redraw();
        }
    };

    /**
     * Permit focus events via keyboard.
     */
    private TraverseListener traverseListener = new TraverseListener() {
        public void keyTraversed(TraverseEvent e) {
        }
    };

    /**
     * Forward the insert key back to our parent for handling.
     */
    private KeyListener keyListener = new KeyListener() {
        public void keyPressed(KeyEvent e) {
            if (e.keyCode == SWT.INSERT)
                parentTable.keyPressed(null, e);
        }

        public void keyReleased(KeyEvent e) {
        }
    };

}
