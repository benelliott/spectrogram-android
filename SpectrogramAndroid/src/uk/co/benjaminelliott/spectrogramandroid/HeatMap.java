package uk.co.benjaminelliott.spectrogramandroid;

import android.util.Log;

public class HeatMap {


	static int[] greyscale() {
		/*
		 * A method which fills the 'colours' array with greyscale values.
		 */

		//Fill backwards because (255, 255, 255) is white, and want white to be low
		int[] toReturn = new int[256];
		for (int i = 0; i < 256; i++) {
			toReturn[255-i] = 255;
			toReturn[255-i] <<= 8;
			toReturn[255-i] += i; 
			toReturn[255-i] <<= 8; 
			toReturn[255-i] += i;
			toReturn[255-i] <<= 8;
			toReturn[255-i] += i; 
			//System.out.println("Alpha: "+Color.alpha(toReturn)+" Red: "+Color.red(toReturn)+" Green: "+Color.green(toReturn)+" Blue: "+Color.blue(toReturn));
		}
		return toReturn;
	}

	static int[] blueGreenRed() {
		/*
		 * A first implementation for a method which fills the 'colours' array with a heatmap-like set of RGB
		 * values.
		 */
		int[] toReturn = new int[256];
		for (int i = 0; i < 256; i++) {
			toReturn[i] = 255;
			toReturn[i] <<= 8;
			toReturn[i] += i; //red
			toReturn[i] <<= 8; //one byte for each colour, MS byte is alpha
			toReturn[i] += (int)(2*(127.5f-Math.abs(i-127.5f))); //green is 127.5 - |i-127.5| (draw it - peak at 127.5)
			toReturn[i] <<= 8;
			toReturn[i] += 255-i; //blue
			//System.out.println("Alpha: "+Color.alpha(toReturn)+" Red: "+Color.red(toReturn)+" Green: "+Color.green(toReturn)+" Blue: "+Color.blue(toReturn));
		}
		return toReturn;
	}

	static int[] bluePinkRed() {
		/*
		 * A first implementation for a method which fills the 'colours' array with a heatmap-like set of RGB
		 * values.
		 */
		int[] toReturn = new int[256];
		for (int i = 0; i < 256; i++) {
			toReturn[i] = 255; //alpha
			toReturn[i] <<= 8;
			if (i < 127) toReturn[i] += 2*i; //red
			else toReturn[i] += 255;
			toReturn[i] <<= 16; //skip green as always 0
			if (i < 127) toReturn[i] += 255; //blue
			toReturn[i] += 2*(255-i);
			//System.out.println("Alpha: "+Color.alpha(toReturn)+" Red: "+Color.red(toReturn)+" Green: "+Color.green(toReturn)+" Blue: "+Color.blue(toReturn));
		}
		return toReturn;
	}

	static int[] blueOrangeYellow() {
		/*
		 * A first implementation for a method which fills the 'colours' array with a heatmap-like set of RGB
		 * values.
		 */
		int[] toReturn = new int[256];
		for (int i = 0; i < 256; i++) {
			toReturn[i] = 255; //alpha
			toReturn[i] <<= 8;
			if (i < 127) toReturn[i] += 2*i; //red
			else toReturn[i] += 255;
			toReturn[i] <<= 8;
			toReturn[i] += i; //green
			toReturn[i] <<= 8;
			if (i < 127) toReturn[i] += 255; //blue
			toReturn[i] += 2*(255-i);
			//System.out.println("Alpha: "+Color.alpha(toReturn)+" Red: "+Color.red(toReturn)+" Green: "+Color.green(toReturn)+" Blue: "+Color.blue(toReturn));
		}
		return toReturn;
	}

	static int[] yellowOrangeBlue() {
		/*
		 * A first implementation for a method which fills the 'colours' array with a heatmap-like set of RGB
		 * values.
		 */
		int[] toReturn = new int[256];
		for (int i = 0; i < 256; i++) {
			toReturn[255 - i] = 255; //alpha
			toReturn[255 - i] <<= 8;
			if (i < 127) toReturn[255 - i] += 2*i; //red
			else toReturn[255 - i] += 255;
			toReturn[255 - i] <<= 8; 
			toReturn[255 - i] += i; //green
			toReturn[255 - i] <<= 8;
			if (i < 127) toReturn[255 - i] += 255; //blue
			toReturn[255 - i] += 2*(255-i);
			//System.out.println("Alpha: "+Color.alpha(toReturn)+" Red: "+Color.red(toReturn)+" Green: "+Color.green(toReturn)+" Blue: "+Color.blue(toReturn));
		}
		return toReturn;
	}

	static int[] blackGreen() {
		/*
		 * A first implementation for a method which fills the 'colours' array with a heatmap-like set of RGB
		 * values.
		 */
		int[] toReturn = new int[256];
		for (int i = 0; i < 256; i++) {
			toReturn[i] = 255; //alpha
			toReturn[i] <<= 8;
			toReturn[i] <<= 8;
			toReturn[i] += i; //green
			toReturn[i] <<= 8;
			//System.out.println("Alpha: "+Color.alpha(toReturn)+" Red: "+Color.red(toReturn)+" Green: "+Color.green(toReturn)+" Blue: "+Color.blue(toReturn));
		}
		return toReturn;
	}

	static int[] blueGreenRed2() {
		//Functions for R,G,B obtained through observation of a colour picker
		int[] toReturn = new int[256];
		for (int i = 0; i < 256; i++) {
			//ALPHA:
			toReturn[i] = 255; //no transparency
			toReturn[i] <<= 8;

			//RED:
			//if i < 135 then 0
			//		if (135 < i && i < 192) toReturn[i] += (int)(255/(192-135))*(i-135);
			//		if (192 <= i) toReturn[i] += 255;
			toReturn[i] += i;
			toReturn[i] <<= 8;

			//GREEN:
			//		if (0 <= i && i < 75) toReturn[i] += (int)(255/75)*(i);
			//		if (75 <= i && i < 192) toReturn[i] += 255;
			//		if (192 <= i) toReturn[i] += (255/(255-192))*(255-i);
			//		toReturn[i] <<= 8;
			//		

			if (0 <= i && i <= 127) toReturn[i] += 2*i;
			if (127 < i && i <= 192) toReturn[i] += (int)255-0.5*i;
			if (i > 192) toReturn[i] += (255/(255-192))*(255-i);
			toReturn[i] <<= 8;

			//BLUE:
			//		if (0 <= i && i < 75) toReturn[i] += 255;
			//		if (75 <= i && i < 135) toReturn[i] += (255/(135-75))*(135-i);
			if (0 <= i && i <= 127) toReturn[i] += 2*(127-i); //else 0
			//if 135 <= i then 0

			//System.out.println("Alpha: "+Color.alpha(toReturn)+" Red: "+Color.red(toReturn)+" Green: "+Color.green(toReturn)+" Blue: "+Color.blue(toReturn));
		}
		return toReturn;

	}

	static int[] whiteBlue() {
		/*
		 * A method which fills the 'colours' array with greyscale values.
		 */

		//Fill backwards because (255, 255, 255) is white, and want white to be low
		int[] toReturn = new int[256];
		for (int i = 0; i < 256; i++) {
			toReturn[255-i] = 255;
			toReturn[255-i] <<= 8;
			toReturn[255-i] += i; //red
			toReturn[255-i] <<= 8; 
			toReturn[255-i] += i; //green
			toReturn[255-i] <<= 8;
			toReturn[255-i] += 255; //blue
			//System.out.println("Alpha: "+Color.alpha(toReturn)+" Red: "+Color.red(toReturn)+" Green: "+Color.green(toReturn)+" Blue: "+Color.blue(toReturn));
		}
		return toReturn;
	}

	static int[] hotMetal() {
		/*
		 * A method which fills the 'colours' array with greyscale values.
		 */

		//Fill backwards because (255, 255, 255) is white, and want white to be low
		int[] toReturn = new int[256];
		for (int i = 0; i < 256; i++) {
			toReturn[i] = 255;
			toReturn[i] <<= 8;
			if (i <= 134) toReturn[i] += i*255/134; //red
			else toReturn[i] += 255;
			toReturn[i] <<= 8; 
			//if i <= 73 then 0; //green
			if (i > 73 && i <= 194)	toReturn[i] += ((i-73)*255)/(194-73);
			else if (i > 194) toReturn[i] += 255;
			toReturn[i] <<= 8;
			// if (i <= 134) then 0; //blue
			if (i >= 134) toReturn[i] += ((i-134)*255)/(255-134);
			//System.out.println("Alpha: "+Color.alpha(toReturn)+" Red: "+Color.red(toReturn)+" Green: "+Color.green(toReturn)+" Blue: "+Color.blue(toReturn));
		}
		return toReturn;
	}

	static int[] whitePurpleGrouped() {
		/*
		 * A method which fills the 'colours' array with greyscale values.
		 */

		//Fill backwards because (255, 255, 255) is white, and want white to be low
		int[] toReturn = new int[256];
		for (int i = 0; i < 256; i++) {
			toReturn[i] = 255; //alpha
			toReturn[i] <<= 8;
			
			//red
			if (0 <= i && i <= 31) toReturn[i] += 255;
			if (32 <= i && i <= 63) toReturn[i] += 253;
			if (64 <= i && i <= 95) toReturn[i] += 252;
			if (96 <= i && i <= 127) toReturn[i] += 250;
			if (128 <= i && i <= 159) toReturn[i] += 247;
			if (160 <= i && i <= 191) toReturn[i] += 221;
			if (192 <= i && i <= 223) toReturn[i] += 174;
			if (224 <= i && i <= 255) toReturn[i] += 122;
			toReturn[i] <<= 8; 
			
			//green
			
			if (0 <= i && i <= 31) toReturn[i] += 247;
			if (32 <= i && i <= 63) toReturn[i] += 224;
			if (64 <= i && i <= 95) toReturn[i] += 197;
			if (96 <= i && i <= 127) toReturn[i] += 159;
			if (128 <= i && i <= 159) toReturn[i] += 104;
			if (160 <= i && i <= 191) toReturn[i] += 52;
			if (192 <= i && i <= 223) toReturn[i] += 1;
			if (224 <= i && i <= 255) toReturn[i] += 1;
			toReturn[i] <<= 8; 
			
			//blue
			if (0 <= i && i <= 31) toReturn[i] += 243;
			if (32 <= i && i <= 63) toReturn[i] += 221;
			if (64 <= i && i <= 95) toReturn[i] += 192;
			if (96 <= i && i <= 127) toReturn[i] += 181;
			if (128 <= i && i <= 159) toReturn[i] += 161;
			if (160 <= i && i <= 191) toReturn[i] += 151;
			if (192 <= i && i <= 223) toReturn[i] += 125;
			if (224 <= i && i <= 255) toReturn[i] += 119;
			//System.out.println("Alpha: "+Color.alpha(toReturn)+" Red: "+Color.red(toReturn)+" Green: "+Color.green(toReturn)+" Blue: "+Color.blue(toReturn));
		}
		return toReturn;
	}
	
	static int[] inverseGreyscale() {
		/*
		 * A method which fills the 'colours' array with greyscale values.
		 */

		//Fill backwards because (255, 255, 255) is white, and want white to be low
		int[] toReturn = new int[256];
		for (int i = 0; i < 256; i++) {
			toReturn[i] = 255; //alpha
			toReturn[i] <<= 8;
			
			//red
			if (0 <= i && i <= 31) toReturn[i] += 255;
			if (32 <= i && i <= 63) toReturn[i] += 240;
			if (64 <= i && i <= 95) toReturn[i] += 220;
			if (96 <= i && i <= 127) toReturn[i] += 190;
			if (128 <= i && i <= 159) toReturn[i] += 150;
			if (160 <= i && i <= 191) toReturn[i] += 100;
			if (192 <= i && i <= 223) toReturn[i] += 50;
			if (224 <= i && i <= 255) toReturn[i] += 0;
			toReturn[i] <<= 8; 
			
			//green
			
			if (0 <= i && i <= 31) toReturn[i] += 255;
			if (32 <= i && i <= 63) toReturn[i] += 240;
			if (64 <= i && i <= 95) toReturn[i] += 220;
			if (96 <= i && i <= 127) toReturn[i] += 190;
			if (128 <= i && i <= 159) toReturn[i] += 150;
			if (160 <= i && i <= 191) toReturn[i] += 100;
			if (192 <= i && i <= 223) toReturn[i] += 50;
			if (224 <= i && i <= 255) toReturn[i] += 0;
			toReturn[i] <<= 8; 
			
			//blue
			if (0 <= i && i <= 31) toReturn[i] += 255;
			if (32 <= i && i <= 63) toReturn[i] += 240;
			if (64 <= i && i <= 95) toReturn[i] += 220;
			if (96 <= i && i <= 127) toReturn[i] += 190;
			if (128 <= i && i <= 159) toReturn[i] += 150;
			if (160 <= i && i <= 191) toReturn[i] += 100;
			if (192 <= i && i <= 223) toReturn[i] += 50;
			if (224 <= i && i <= 255) toReturn[i] += 0;
			//System.out.println("Alpha: "+Color.alpha(toReturn)+" Red: "+Color.red(toReturn)+" Green: "+Color.green(toReturn)+" Blue: "+Color.blue(toReturn));
		}
		return toReturn;
	}

}
