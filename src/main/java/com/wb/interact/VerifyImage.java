package com.wb.interact;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.wb.common.Var;
import com.wb.util.StringUtil;

/**
 * 图片验证码生成器。
 */
public class VerifyImage {
	public static void outputImage(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		response.setHeader("Cache-Control",
				"no-cache, no-store, max-age=0, must-revalidate");
		response.setContentType("image/jpeg");
		int width = 90, height = 20, i, x, y, xl, yl, fontStyle;
		String varPrefix = "sys.session.verifyImage.", styles[] = { "plain",
				"bold", "italic" };
		fontStyle = StringUtil.indexOf(styles, Var.getString(
				varPrefix + "fontStyle").toLowerCase());
		if (fontStyle == -1)
			fontStyle = 1;
		Font font = new Font(Var.getString(varPrefix + "fontName"), fontStyle,
				16);
		BufferedImage image = new BufferedImage(width, height,
				BufferedImage.TYPE_INT_RGB);
		Graphics g = image.getGraphics();
		try {
			Random random = new Random();
			g.setColor(getRandColor(200, 250));
			g.fillRect(1, 1, width - 1, height - 1);
			g.setColor(new Color(102, 102, 102));
			g.drawRect(0, 0, width - 1, height - 1);
			g.setFont(font);
			g.setColor(getRandColor(160, 200));
			for (i = 0; i < 155; i++) {
				x = random.nextInt(width - 1);
				y = random.nextInt(height - 1);
				xl = random.nextInt(6) + 1;
				yl = random.nextInt(12) + 1;
				g.drawLine(x, y, x + xl, y + yl);
			}
			for (i = 0; i < 70; i++) {
				x = random.nextInt(width - 1);
				y = random.nextInt(height - 1);
				xl = random.nextInt(12) + 1;
				yl = random.nextInt(6) + 1;
				g.drawLine(x, y, x - xl, y - yl);
			}
			StringBuilder rand = new StringBuilder(5);
			String str, key;
			for (i = 0; i < 5; i++) {
				str = getRandomChar();
				rand.append(str);
				g.setColor(new Color(20 + random.nextInt(110), 20 + random
						.nextInt(110), 20 + random.nextInt(110)));
				g.drawString(str, 16 * i + 7, 16);
			}
			HttpSession session = request.getSession(true);
			key = request.getParameter("key");
			if (StringUtil.isEmpty(key))
				key = "sys.verifyCode";
			else if (key.indexOf('.') != -1)
				throw new Exception("Illegal key.");
			session.setAttribute(key, rand.toString());
		} finally {
			g.dispose();
		}
		ImageIO.write(image, "jpeg", response.getOutputStream());
		response.flushBuffer();
	}

	private static Color getRandColor(int fc, int bc) {
		Random random = new Random();
		int r = fc + random.nextInt(bc - fc);
		int g = fc + random.nextInt(bc - fc);
		int b = fc + random.nextInt(bc - fc);
		return new Color(r, g, b);
	}

	private static String getRandomChar() {
		int rand = (int) Math.round(Math.random() * 2);
		long itmp;
		char ctmp;

		switch (rand) {
		case 1:
			itmp = Math.round(Math.random() * 25 + 65);
			ctmp = (char) itmp;
			if (ctmp == 'I' || ctmp == 'L')
				ctmp = '1';
			else if (ctmp == 'O')
				ctmp = '0';
			return String.valueOf(ctmp);
		case 2:
			itmp = Math.round(Math.random() * 25 + 97);
			ctmp = (char) itmp;
			if (ctmp == 'i' || ctmp == 'l')
				ctmp = '1';
			else if (ctmp == 'o')
				ctmp = '0';
			return String.valueOf(ctmp);
		default:
			itmp = Math.round(Math.random() * 9);
			return String.valueOf(itmp);
		}
	}
}