package org.apache.batik.ext.awt.image;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImagingOpException;

/**
 * Utility class for <em>save</em> image filtering operations to workaround
 * JDK 1.7.0_25 image filtering bugs(s).
 *
 * @author Thomas Behr
 */
public class FilterUtil {
  private FilterUtil() {
  }

  public static BufferedImage filter(
          final BufferedImageOp op, final BufferedImage src
  ) {
    return filter(op, src, op.createCompatibleDestImage(src, null));
  }

  public static BufferedImage filter(
          final BufferedImageOp op, final BufferedImage src, final BufferedImage dst
  ) {
    try {
      return op.filter(src, dst);
    } catch (ImagingOpException e) {
      final BufferedImage _src = copy(src);
      final BufferedImage _dst = copy(dst);
      op.filter(_src, _dst);

      final Graphics2D gfx = dst.createGraphics();
      gfx.drawImage(_dst, 0, 0, null);
      gfx.dispose();

      return dst;
    }
  }

  private static BufferedImage copy( final BufferedImage img ) {
    final BufferedImage copy = new BufferedImage(
            img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
    final Graphics2D gfx = copy.createGraphics();
    gfx.drawImage(img, 0, 0, null);
    gfx.dispose();
    return copy;
  }
}
