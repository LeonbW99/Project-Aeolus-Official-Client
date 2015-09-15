package com.aeolus;

import java.applet.AppletContext;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import com.aeolus.cache.Index;
import com.aeolus.cache.config.MessageCensor;
import com.aeolus.cache.config.VariableBits;
import com.aeolus.cache.config.VariableParameter;
import com.aeolus.cache.def.NpcDefinition;
import com.aeolus.cache.def.IdentityKit;
import com.aeolus.cache.def.ItemDefinition;
import com.aeolus.cache.def.ObjectDefinition;
import com.aeolus.cache.def.SpotAnimation;
import com.aeolus.cache.media.Background;
import com.aeolus.cache.media.Widget;
import com.aeolus.cache.media.SequenceFrame;
import com.aeolus.cache.media.Sprite;
import com.aeolus.collection.Linkable;
import com.aeolus.collection.Deque;
import com.aeolus.media.Animation;
import com.aeolus.media.Raster;
import com.aeolus.media.ImageProducer;
import com.aeolus.media.font.RSFont;
import com.aeolus.media.font.TextClass;
import com.aeolus.media.font.GameFont;
import com.aeolus.media.font.TextInput;
import com.aeolus.media.renderable.Renderable;
import com.aeolus.media.renderable.Item;
import com.aeolus.media.renderable.Model;
import com.aeolus.media.renderable.ObjectManager;
import com.aeolus.media.renderable.StaticObject;
import com.aeolus.media.renderable.TemporaryObject;
import com.aeolus.media.renderable.entity.Entity;
import com.aeolus.media.renderable.entity.Npc;
import com.aeolus.media.renderable.entity.Player;
import com.aeolus.net.NetworkConstants;
import com.aeolus.net.RSSocket;
import com.aeolus.net.Buffer;
import com.aeolus.net.CacheArchive;
import com.aeolus.net.requester.OnDemandNode;
import com.aeolus.net.requester.OnDemandRequester;
import com.aeolus.net.security.ISAACCipher;
import com.aeolus.net.security.UserIdentification;
import com.aeolus.scene.SceneObject;
import com.aeolus.scene.SceneProjectile;
import com.aeolus.scene.SceneSpotAnim;
import com.aeolus.scene.graphic.Fog;
import com.aeolus.scene.graphic.Rasterizer;
import com.aeolus.scene.map.CollisionMap;
import com.aeolus.scene.map.SceneGraph;
import com.aeolus.scene.tile.Floor;
import com.aeolus.scene.tile.GroundDecoration;
import com.aeolus.scene.tile.WallDecoration;
import com.aeolus.scene.tile.WallLock;
import com.aeolus.sound.SoundConstants;
import com.aeolus.sound.SoundPlayer;
import com.aeolus.sound.SoundTrack;
import com.aeolus.util.MouseDetection;
import com.aeolus.util.PacketConstants;
import com.aeolus.util.SkillConstants;
import com.aeolus.util.signlink.Signlink;

public class Game extends GameShell {

	public enum ScreenMode {
		FIXED, RESIZABLE, FULLSCREEN;
	}

	private Fog depthBuffer = new Fog();

	private int[][] xp_added = new int[10][3];
	private Sprite[] skill_sprites = new Sprite[SkillConstants.skillsCount];

	public static ScreenMode frameMode = ScreenMode.FIXED;
	public static int frameWidth = 765;
	public static int frameHeight = 503;
	public static int screenAreaWidth = 512;
	public static int screenAreaHeight = 334;
	public static int cameraZoom = 600;
	public static boolean showChatComponents = true;
	public static boolean showTabComponents = true;
	public static boolean changeTabArea = frameMode == ScreenMode.FIXED ? false
			: true;
	public static boolean changeChatArea = frameMode == ScreenMode.FIXED ? false
			: true;
	public static boolean transparentTabArea = false;
	private final int[] soundVolume;

	public static void frameMode(ScreenMode screenMode) {
		if (frameMode != screenMode) {
			frameMode = screenMode;
			if (screenMode == ScreenMode.FIXED) {
				frameWidth = 765;
				frameHeight = 503;
				cameraZoom = 600;
				SceneGraph.viewDistance = 9;
				changeChatArea = false;
				changeTabArea = false;
			} else if (screenMode == ScreenMode.RESIZABLE) {
				frameWidth = 766;
				frameHeight = 529;
				cameraZoom = 850;
				SceneGraph.viewDistance = 10;
			} else if (screenMode == ScreenMode.FULLSCREEN) {
				cameraZoom = 600;
				SceneGraph.viewDistance = 10;
				frameWidth = (int) Toolkit.getDefaultToolkit().getScreenSize()
						.getWidth();
				frameHeight = (int) Toolkit.getDefaultToolkit().getScreenSize()
						.getHeight();
			}
			rebuildFrameSize(screenMode, frameWidth, frameHeight);
			setBounds();
			System.out.println("ScreenMode: " + screenMode.toString());
		}
		showChatComponents = screenMode == ScreenMode.FIXED ? true
				: showChatComponents;
		showTabComponents = screenMode == ScreenMode.FIXED ? true
				: showTabComponents;
	}

	/* Music Packer */

	public void musics() {
		for (int MusicIndex = 0; MusicIndex < 3536; MusicIndex++) {
			byte[] song = GetMusic(MusicIndex);
			if (song != null && song.length > 0) {
				decompressors[3].method234(song.length, song, MusicIndex);
			}
		}
	}

	public byte[] GetMusic(int Index) {
		try {
			File Music = new File(Signlink.findcachedir() + "./Music/" + Index
					+ ".gz");
			byte[] song = new byte[(int) Music.length()];
			FileInputStream Fis = new FileInputStream(Music);
			Fis.read(song);
			System.out.println("" + Index + " aByte = [" + song + "]!");
			Fis.close();
			return song;
		} catch (Exception e) {
			return null;
		}
	}

	/* End of Music Packer */

	/**
	 * Repack/Dump variables
	 */
	final int MODELS = 1;
	final int ANIMS = 2;
	final int MIDI = 3;
	final int WORLD = 4;

	/**
	 * Dumps the cache of specified index
	 */
	public void dumpCacheIndex(int cacheIndex) {
		System.out.println("Unpacking index" + cacheIndex);
		try {
			for (int i = 0;; i++) {
				try {
					byte[] indexByteArray = decompressors[cacheIndex]
							.decompress(i);
					if (indexByteArray == null) {
						System.out.println("Finished dumping index "
								+ cacheIndex + ", exiting dump operation.");
						break;
					}
					BufferedOutputStream gzip = new BufferedOutputStream(
							new GZIPOutputStream(new FileOutputStream(
									Signlink.findcachedir() + "dump"
											+ cacheIndex + "/" + i + ".gz")));
					if (indexByteArray.length == 0)
						continue;
					else {
						gzip.write(indexByteArray);
						System.out.println("Unpacked " + i + ".");
						gzip.close();

					}
				} catch (IOException io) {
					throw new IOException(
							"Error writing to folder. Ensure you have this directory created: '"
									+ Signlink.findcachedir() + "dump"
									+ cacheIndex + "'");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String indexLocation(int cacheIndex, int index) {
		return Signlink.findcachedir() + "index" + cacheIndex + "/"
				+ (index != -1 ? index + ".gz" : "");
	}

	public void repackCacheIndex(int cacheIndex) {
		System.out.println("Started repacking index " + cacheIndex + ".");

		try {
			int indexLength = new File(indexLocation(cacheIndex, -1)).listFiles().length;
			File[] file = new File(indexLocation(cacheIndex, -1)).listFiles();
			for (int index = 0; index < indexLength; index++) {
				int fileIndex = Integer
						.parseInt(getFileNameWithoutExtension(file[index]
								.toString()));
				byte[] data = fileToByteArray(cacheIndex, fileIndex);
				if (data != null && data.length > 0) {
					decompressors[cacheIndex].method234(data.length, data,
							fileIndex);
					System.out.println("Repacked " + fileIndex + ".");
				} else {
					System.out.println("Unable to locate index " + fileIndex
							+ ".");
				}
			}
		} catch (Exception e) {
			System.out.println("Error packing cache index " + cacheIndex + ".");
		}
		System.out.println("Finished repacking " + cacheIndex + ".");
	}

	private void addToXPCounter(int skill, int xp) {
		int font_height = 24;
		if (xp <= 0)
			return;

		xpCounter += xp;

		int lowest_y_off = Integer.MAX_VALUE;
		for (int i = 0; i < xp_added.length; i++)
			if (xp_added[i][0] > -1)
				lowest_y_off = Math.min(lowest_y_off, xp_added[i][2]);

		if (Configuration.xp_merge && lowest_y_off != Integer.MAX_VALUE
				&& lowest_y_off <= 0) {
			for (int i = 0; i < xp_added.length; i++) {
				if (xp_added[i][2] != lowest_y_off)
					continue;

				xp_added[i][0] |= (1 << skill);
				xp_added[i][1] += xp;
				return;
			}
		} else {
			ArrayList<Integer> list = new ArrayList<Integer>();
			int y = font_height;

			boolean go_on = true;
			while (go_on) {
				go_on = false;

				for (int i = 0; i < xp_added.length; i++) {
					if (xp_added[i][0] == -1 || list.contains(new Integer(i)))
						continue;

					if (xp_added[i][2] < y) {
						xp_added[i][2] = y;
						y += font_height;
						go_on = true;
						list.add(new Integer(i));
					}
				}
			}

			if (lowest_y_off == Integer.MAX_VALUE
					|| lowest_y_off >= font_height)
				lowest_y_off = 0;
			else
				lowest_y_off = 0;

			for (int i = 0; i < xp_added.length; i++)
				if (xp_added[i][0] == -1) {
					xp_added[i][0] = (1 << skill);
					xp_added[i][1] = xp;
					xp_added[i][2] = lowest_y_off;
					return;
				}
		}
		System.out.println("Failed to add to exp counter.");
	}

	public static void rebuildFrameSize(ScreenMode screenMode, int screenWidth,
			int screenHeight) {
		try {
			screenAreaWidth = (screenMode == ScreenMode.FIXED) ? 512
					: screenWidth;
			screenAreaHeight = (screenMode == ScreenMode.FIXED) ? 334
					: screenHeight;
			frameWidth = screenWidth;
			frameHeight = screenHeight;
			instance.refreshFrameSize(screenMode == ScreenMode.FULLSCREEN,
					screenWidth, screenHeight,
					screenMode == ScreenMode.RESIZABLE,
					screenMode != ScreenMode.FIXED);
			setBounds();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void refreshFrameSize() {
		if (frameMode == ScreenMode.RESIZABLE) {
			if (frameWidth != (appletClient() ? getGameComponent().getWidth()
					: gameFrame.getFrameWidth())) {
				frameWidth = (appletClient() ? getGameComponent().getWidth()
						: gameFrame.getFrameWidth());
				screenAreaWidth = frameWidth;
				setBounds();
			}
			if (frameHeight != (appletClient() ? getGameComponent().getHeight()
					: gameFrame.getFrameHeight())) {
				frameHeight = (appletClient() ? getGameComponent().getHeight()
						: gameFrame.getFrameHeight());
				screenAreaHeight = frameHeight;
				setBounds();
			}
		}
	}

	private static void setBounds() {
		Rasterizer.method365(frameWidth, frameHeight);
		fullScreenTextureArray = Rasterizer.anIntArray1472;
		Rasterizer
				.method365(
						frameMode == ScreenMode.FIXED ? (chatboxImageProducer != null ? chatboxImageProducer.canvasWidth
								: 519)
								: frameWidth,
						frameMode == ScreenMode.FIXED ? (chatboxImageProducer != null ? chatboxImageProducer.canvasHeight
								: 165)
								: frameHeight);
		anIntArray1180 = Rasterizer.anIntArray1472;
		Rasterizer
				.method365(
						frameMode == ScreenMode.FIXED ? (tabImageProducer != null ? tabImageProducer.canvasWidth
								: 249)
								: frameWidth,
						frameMode == ScreenMode.FIXED ? (tabImageProducer != null ? tabImageProducer.canvasHeight
								: 335)
								: frameHeight);
		anIntArray1181 = Rasterizer.anIntArray1472;
		Rasterizer.method365(screenAreaWidth, screenAreaHeight);
		anIntArray1182 = Rasterizer.anIntArray1472;
		int ai[] = new int[9];
		for (int i8 = 0; i8 < 9; i8++) {
			int k8 = 128 + i8 * 32 + 15;
			int l8 = 600 + k8 * 3;
			int i9 = Rasterizer.anIntArray1470[k8];
			ai[i8] = l8 * i9 >> 16;
		}
		if (frameMode == ScreenMode.RESIZABLE && (frameWidth >= 766)
				&& (frameWidth <= 1025) && (frameHeight >= 504)
				&& (frameHeight <= 850)) {
			SceneGraph.viewDistance = 9;
			cameraZoom = 575;
		} else if (frameMode == ScreenMode.FIXED) {
			cameraZoom = 600;
		} else if (frameMode == ScreenMode.RESIZABLE
				|| frameMode == ScreenMode.FULLSCREEN) {
			SceneGraph.viewDistance = 10;
			cameraZoom = 600;
		}
		SceneGraph.method310(500, 800, screenAreaWidth, screenAreaHeight, ai);
		if (loggedIn) {
			gameScreenImageProducer = new ImageProducer(screenAreaWidth,
					screenAreaHeight);
		}
	}

	public boolean getMousePositions() {
		if (mouseInRegion(frameWidth - (frameWidth <= 1000 ? 240 : 420),
				frameHeight - (frameWidth <= 1000 ? 90 : 37), frameWidth,
				frameHeight)) {
			return false;
		}
		if (showChatComponents) {
			if (changeChatArea) {
				if (super.mouseX > 0 && super.mouseX < 494
						&& super.mouseY > frameHeight - 175
						&& super.mouseY < frameHeight) {
					return true;
				} else {
					if (super.mouseX > 494 && super.mouseX < 515
							&& super.mouseY > frameHeight - 175
							&& super.mouseY < frameHeight) {
						return false;
					}
				}
			} else if (!changeChatArea) {
				if (super.mouseX > 0 && super.mouseX < 519
						&& super.mouseY > frameHeight - 175
						&& super.mouseY < frameHeight) {
					return false;
				}
			}
		}
		if (mouseInRegion(frameWidth - 216, 0, frameWidth, 172)) {
			return false;
		}
		if (!changeTabArea) {
			if (super.mouseX > 0 && super.mouseY > 0
					&& super.mouseY < frameWidth && super.mouseY < frameHeight) {
				if (super.mouseX >= frameWidth - 242
						&& super.mouseY >= frameHeight - 335) {
					return false;
				}
				return true;
			}
			return false;
		}
		if (showTabComponents) {
			if (frameWidth > 1000) {
				if (super.mouseX >= frameWidth - 420
						&& super.mouseX <= frameWidth
						&& super.mouseY >= frameHeight - 37
						&& super.mouseY <= frameHeight
						|| super.mouseX > frameWidth - 225
						&& super.mouseX < frameWidth
						&& super.mouseY > frameHeight - 37 - 274
						&& super.mouseY < frameHeight) {
					return false;
				}
			} else {
				if (super.mouseX >= frameWidth - 210
						&& super.mouseX <= frameWidth
						&& super.mouseY >= frameHeight - 74
						&& super.mouseY <= frameHeight
						|| super.mouseX > frameWidth - 225
						&& super.mouseX < frameWidth
						&& super.mouseY > frameHeight - 74 - 274
						&& super.mouseY < frameHeight) {
					return false;
				}
			}
		}
		return true;
	}

	public boolean mouseInRegion(int x1, int y1, int x2, int y2) {
		if (super.mouseX >= x1 && super.mouseX <= x2 && super.mouseY >= y1
				&& super.mouseY <= y2) {
			return true;
		}
		return false;
	}

	public boolean mouseMapPosition() {
		if (super.mouseX >= frameWidth - 21 && super.mouseX <= frameWidth
				&& super.mouseY >= 0 && super.mouseY <= 21) {
			return false;
		}
		return true;
	}

	private void drawLoadingMessages(int used, String s, String s1) {
		int width = regularText.getTextWidth(used == 1 ? s : s1);
		int height = s1 == null ? 25 : 38;
		Raster.drawPixels(height, 1, 1, 0, width + 6);
		Raster.drawPixels(1, 1, 1, 0xffffff, width + 6);
		Raster.drawPixels(height, 1, 1, 0xffffff, 1);
		Raster.drawPixels(1, height, 1, 0xffffff, width + 6);
		Raster.drawPixels(height, 1, width + 6, 0xffffff, 1);
		regularText.drawText(0xffffff, s, 18, width / 2 + 5);
		if (s1 != null) {
			regularText.drawText(0xffffff, s1, 31, width / 2 + 5);
		}
	}

	private static final long serialVersionUID = 5707517957054703648L;

	private static String intToKOrMilLongName(int i) {
		String s = String.valueOf(i);
		for (int k = s.length() - 3; k > 0; k -= 3)
			s = s.substring(0, k) + "," + s.substring(k);
		if (s.length() > 8)
			s = "@gre@" + s.substring(0, s.length() - 8) + " million @whi@("
					+ s + ")";
		else if (s.length() > 4)
			s = "@cya@" + s.substring(0, s.length() - 4) + "K @whi@(" + s + ")";
		return " " + s;
	}

	public final String methodR(int j) {
		if (j >= 0 && j < 10000)
			return String.valueOf(j);
		if (j >= 10000 && j < 10000000)
			return j / 1000 + "K";
		if (j >= 10000000 && j < 999999999)
			return j / 1000000 + "M";
		if (j >= 999999999)
			return "*";
		else
			return "?";
	}

	public static final byte[] ReadFile(String s) {
		try {
			byte abyte0[];
			File file = new File(s);
			int i = (int) file.length();
			abyte0 = new byte[i];
			DataInputStream datainputstream = new DataInputStream(
					new BufferedInputStream(new FileInputStream(s)));
			datainputstream.readFully(abyte0, 0, i);
			datainputstream.close();
			return abyte0;
		} catch (Exception e) {
			System.out.println((new StringBuilder()).append("Read Error: ")
					.append(s).toString());
			return null;
		}
	}

	private boolean menuHasAddFriend(int j) {
		if (j < 0)
			return false;
		int k = menuActionID[j];
		if (k >= 2000)
			k -= 2000;
		return k == 337;
	}

	private final int[] modeX = { 164, 230, 296, 362 }, modeNamesX = { 26, 86,
			150, 212, 286, 349, 427 }, modeNamesY = { 158, 158, 153, 153, 153,
			153, 158 }, channelButtonsX = { 5, 71, 137, 203, 269, 335, 404 };

	private final String[] modeNames = { "All", "Game", "Public", "Private",
			"Clan", "Trade", "Report Abuse" };

	public void drawChannelButtons() {
		final int yOffset = frameMode == ScreenMode.FIXED ? 0
				: frameHeight - 165;
		cacheSprite[49].drawSprite(0, 143 + yOffset);
		String text[] = { "On", "Friends", "Off", "Hide" };
		int textColor[] = { 65280, 0xffff00, 0xff0000, 65535 };
		switch (cButtonCPos) {
		case 0:
		case 1:
		case 2:
		case 3:
		case 4:
		case 5:
		case 6:
			cacheSprite[16].drawSprite(channelButtonsX[cButtonCPos],
					143 + yOffset);
			break;
		}
		if (cButtonHPos == cButtonCPos) {
			switch (cButtonHPos) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
				cacheSprite[17].drawSprite(channelButtonsX[cButtonHPos],
						143 + yOffset);
				break;
			}
		} else {
			switch (cButtonHPos) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				cacheSprite[15].drawSprite(channelButtonsX[cButtonHPos],
						143 + yOffset);
				break;
			case 6:
				cacheSprite[18].drawSprite(channelButtonsX[cButtonHPos],
						143 + yOffset);
				break;
			}
		}
		int[] modes = { publicChatMode, privateChatMode, clanChatMode,
				tradeMode };
		for (int i = 0; i < modeNamesX.length; i++) {
			smallText.drawTextWithPotentialShadow(true, modeNamesX[i],
					0xffffff, modeNames[i], modeNamesY[i] + yOffset);
		}
		for (int i = 0; i < modeX.length; i++) {
			smallText.method382(textColor[modes[i]], modeX[i], text[modes[i]],
					164 + yOffset, true);
		}
	}

	private boolean chatStateCheck() {
		return messagePromptRaised || inputDialogState != 0
				|| clickToContinueString != null || backDialogueId != -1
				|| dialogueId != -1;
	}

	private void drawChatArea() {
		int yOffset = frameMode == ScreenMode.FIXED ? 0 : frameHeight - 165;
		if (frameMode == ScreenMode.FIXED) {
			chatboxImageProducer.initDrawingArea();
		}
		Rasterizer.anIntArray1472 = anIntArray1180;
		if (chatStateCheck()) {
			showChatComponents = true;
			cacheSprite[20].drawSprite(0, yOffset);
		}
		if (showChatComponents) {
			if (changeChatArea && !chatStateCheck()) {
				Raster.method339(7 + yOffset, 0x575757, 506, 7);
				Raster.drawAlphaGradient(7, 7 + yOffset, 506, 135, 0, 0xFFFFFF,
						20);
			} else {
				cacheSprite[20].drawSprite(0, yOffset);
			}
		}
		if (!showChatComponents || changeChatArea) {
			Raster.drawAlphaPixels(7, frameHeight - 23, 506, 24, 0, 100);
		}
		drawChannelButtons();
		GameFont textDrawingArea = regularText;
		if (messagePromptRaised) {
			newBoldFont.drawCenteredString(aString1121, 259, 60 + yOffset, 0,
					-1);
			newBoldFont.drawCenteredString(promptInput + "*", 259,
					80 + yOffset, 128, -1);
		} else if (inputDialogState == 1) {
			newBoldFont.drawCenteredString("Enter amount:", 259, yOffset + 60,
					0, -1);
			newBoldFont.drawCenteredString(amountOrNameInput + "*", 259,
					80 + yOffset, 128, -1);
		} else if (inputDialogState == 2) {
			newBoldFont.drawCenteredString("Enter Name:", 259, 60 + yOffset, 0,
					-1);
			newBoldFont.drawCenteredString(amountOrNameInput + "*", 259,
					80 + yOffset, 128, -1);
		} else if (clickToContinueString != null) {
			newBoldFont.drawCenteredString(clickToContinueString, 259,
					60 + yOffset, 0, -1);
			newBoldFont.drawCenteredString("Click to continue", 259,
					80 + yOffset, 128, -1);
		} else if (backDialogueId != -1) {
			drawInterface(0, 20, Widget.interfaceCache[backDialogueId],
					20 + yOffset);
		} else if (dialogueId != -1) {
			drawInterface(0, 20, Widget.interfaceCache[dialogueId],
					20 + yOffset);
		} else if (showChatComponents) {
			int j77 = -3;
			int j = 0;
			int shadow = changeChatArea ? 0 : -1;
			Raster.setDrawingArea(122 + yOffset, 8, 497, 7 + yOffset);
			for (int k = 0; k < 500; k++) {
				if (chatMessages[k] != null) {
					int chatType = chatTypes[k];
					int yPos = (70 - j77 * 14) + anInt1089 + 5;
					String s1 = chatNames[k];
					byte byte0 = 0;
					if (s1 != null && s1.startsWith("@cr1@")) {
						s1 = s1.substring(5);
						byte0 = 1;
					} else if (s1 != null && s1.startsWith("@cr2@")) {
						s1 = s1.substring(5);
						byte0 = 2;
					} else if (s1 != null && s1.startsWith("@cr3@")) {
						s1 = s1.substring(5);
						byte0 = 3;
					}
					if (chatType == 0) {
						if (chatTypeView == 5 || chatTypeView == 0) {
							newRegularFont.drawBasicString(chatMessages[k], 11,
									yPos + yOffset, changeChatArea ? 0xFFFFFF
											: 0, shadow);
							j++;
							j77++;
						}
					}
					if ((chatType == 1 || chatType == 2)
							&& (chatType == 1 || publicChatMode == 0 || publicChatMode == 1
									&& isFriendOrSelf(s1))) {
						if (chatTypeView == 1 || chatTypeView == 0) {
							int xPos = 11;
							if (byte0 == 1) {
								modIcons[0].drawSprite(xPos + 1, yPos - 12
										+ yOffset);
								xPos += 14;
							} else if (byte0 == 2) {
								modIcons[1].drawSprite(xPos + 1, yPos - 12
										+ yOffset);
								xPos += 14;
							} else if (byte0 == 3) {
								modIcons[2].drawSprite(xPos + 1, yPos - 12
										+ yOffset);
								xPos += 14;
							}
							newRegularFont.drawBasicString(s1 + ":", xPos, yPos
									+ yOffset, changeChatArea ? 0xFFFFFF : 0,
									shadow);
							xPos += textDrawingArea.getTextWidth(s1) + 8;
							newRegularFont.drawBasicString(chatMessages[k],
									xPos, yPos + yOffset,
									changeChatArea ? 0x7FA9FF : 255, shadow);
							j++;
							j77++;
						}
					}
					if ((chatType == 3 || chatType == 7)
							&& (splitPrivateChat == 0 || chatTypeView == 2)
							&& (chatType == 7 || privateChatMode == 0 || privateChatMode == 1
									&& isFriendOrSelf(s1))) {
						if (chatTypeView == 2 || chatTypeView == 0) {
							int k1 = 11;
							newRegularFont.drawBasicString("From", k1, yPos
									+ yOffset, changeChatArea ? 0 : 0xFFFFFF,
									shadow);
							k1 += textDrawingArea.getTextWidth("From ");
							if (byte0 == 1) {
								modIcons[0].drawSprite(k1, yPos - 12 + yOffset);
								k1 += 12;
							} else if (byte0 == 2) {
								modIcons[1].drawSprite(k1, yPos - 12 + yOffset);
								k1 += 12;
							} else if (byte0 == 3) {
								modIcons[2].drawSprite(k1, yPos - 12 + yOffset);
								k1 += 12;
							}
							newRegularFont.drawBasicString(s1 + ":", k1, yPos
									+ yOffset, changeChatArea ? 0xFFFFFF : 0,
									shadow);
							k1 += textDrawingArea.getTextWidth(s1) + 8;
							newRegularFont.drawBasicString(chatMessages[k], k1,
									yPos + yOffset, 0x800080, shadow);
							j++;
							j77++;
						}
					}
					if (chatType == 4
							&& (tradeMode == 0 || tradeMode == 1
									&& isFriendOrSelf(s1))) {
						if (chatTypeView == 3 || chatTypeView == 0) {
							newRegularFont.drawBasicString(s1 + " "
									+ chatMessages[k], 11, yPos + yOffset,
									0x800080, shadow);
							j++;
							j77++;
						}
					}
					if (chatType == 5 && splitPrivateChat == 0
							&& privateChatMode < 2) {
						if (chatTypeView == 2 || chatTypeView == 0) {
							newRegularFont.drawBasicString(s1 + " "
									+ chatMessages[k], 11, yPos + yOffset,
									0x800080, shadow);
							j++;
							j77++;
						}
					}
					if (chatType == 6
							&& (splitPrivateChat == 0 || chatTypeView == 2)
							&& privateChatMode < 2) {
						if (chatTypeView == 2 || chatTypeView == 0) {
							newRegularFont.drawBasicString("To " + s1 + ":",
									11, yPos + yOffset,
									changeChatArea ? 0xFFFFFF : 0, shadow);
							newRegularFont.drawBasicString(
									chatMessages[k],
									15 + textDrawingArea.getTextWidth("To :"
											+ s1), yPos + yOffset, 0x800080,
									shadow);
							j++;
							j77++;
						}
					}
					if (chatType == 8
							&& (tradeMode == 0 || tradeMode == 1
									&& isFriendOrSelf(s1))) {
						if (chatTypeView == 3 || chatTypeView == 0) {
							newRegularFont.drawBasicString(s1 + " "
									+ chatMessages[k], 11, yPos + yOffset,
									0x7e3200, shadow);
							j++;
							j77++;
						}
						if (chatType == 11 && (clanChatMode == 0)) {
							if (chatTypeView == 11) {
								newRegularFont.drawBasicString(s1 + " "
										+ chatMessages[k], 11, yPos + yOffset,
										0x7e3200, shadow);
								j++;
								j77++;
							}
							if (chatType == 12) {
								newRegularFont.drawBasicString(chatMessages[k]
										+ "", 11, yPos + yOffset, 0x7e3200,
										shadow);
								j++;
							}
						}
					}
					if (chatType == 16) {
						int j2 = 40;
						int clanNameWidth = textDrawingArea
								.getTextWidth(clanname);
						if (chatTypeView == 11 || chatTypeView == 0) {
							switch (chatRights[k]) {
							case 1:
								j2 += clanNameWidth;
								modIcons[0].drawSprite(j2 - 18, yPos - 12
										+ yOffset);
								j2 += 14;
								break;

							case 2:
								j2 += clanNameWidth;
								modIcons[1].drawSprite(j2 - 18, yPos - 12
										+ yOffset);
								j2 += 14;
								break;

							case 3:
								j2 += clanNameWidth;
								modIcons[1].drawSprite(j2 - 18, yPos - 12
										+ yOffset);
								j2 += 14;
								break;

							default:
								j2 += clanNameWidth;
								break;
							}
							newRegularFont.drawBasicString("[", 8, yPos
									+ yOffset, changeChatArea ? 0xFFFFFF : 0,
									shadow);
							newRegularFont.drawBasicString(clanname, 14, yPos
									+ yOffset, changeChatArea ? 0x7FA9FF : 255,
									shadow);
							newRegularFont.drawBasicString("]",
									clanNameWidth + 14, yPos + yOffset,
									changeChatArea ? 0xFFFFFF : 0, shadow);
							newRegularFont.drawBasicString(chatNames[k] + ":",
									j2 - 17, yPos + yOffset,
									changeChatArea ? 0xFFFFFF : 0, shadow);
							j2 += textDrawingArea.getTextWidth(chatNames[k]) + 7;
							newRegularFont.drawBasicString(chatMessages[k],
									j2 - 16, yPos + yOffset, 0x800080, shadow);
							j++;
							j77++;
						}
					}
				}
			}
			Raster.defaultDrawingAreaSize();
			anInt1211 = j * 14 + 7 + 5;
			if (anInt1211 < 111) {
				anInt1211 = 111;
			}
			drawScrollbar(114, anInt1211 - anInt1089 - 113, 7 + yOffset, 496,
					anInt1211, changeChatArea);
			String s;
			if (localPlayer != null && localPlayer.name != null) {
				s = localPlayer.name;
			} else {
				s = TextClass.fixName(capitalize(myUsername));
			}
			Raster.setDrawingArea(140 + yOffset, 8, 509, 120 + yOffset);
			int xOffset = 0;
			if (myPrivilege > 0) {
				modIcons[myPrivilege - 1].drawSprite(10, 122 + yOffset);
				xOffset += 14;
			}
			newRegularFont.drawBasicString(s + ":", xOffset + 11,
					133 + yOffset, changeChatArea ? 0xFFFFFF : 0, shadow);
			newRegularFont.drawBasicString(inputString + "*", xOffset + 12
					+ textDrawingArea.getTextWidth(s + ": "), 133 + yOffset,
					changeChatArea ? 0x7FA9FF : 255, shadow);
			Raster.method339(121 + yOffset, changeChatArea ? 0x575757
					: 0x807660, 506, 7);
			Raster.defaultDrawingAreaSize();
		}
		if (menuOpen) {
			drawMenu(0, frameMode == ScreenMode.FIXED ? 338 : 0);
		}
		if (frameMode == ScreenMode.FIXED) {
			chatboxImageProducer.drawGraphics(338, super.graphics, 0);
		}
		gameScreenImageProducer.initDrawingArea();
		Rasterizer.anIntArray1472 = anIntArray1182;
	}

	public static String capitalize(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (i == 0) {
				s = String.format("%s%s", Character.toUpperCase(s.charAt(0)),
						s.substring(1));
			}
			if (!Character.isLetterOrDigit(s.charAt(i))) {
				if (i + 1 < s.length()) {
					s = String.format("%s%s%s", s.subSequence(0, i + 1),
							Character.toUpperCase(s.charAt(i + 1)),
							s.substring(i + 2));
				}
			}
		}
		return s;
	}

	public void init() {
		try {
			nodeID = 10;
			portOff = 0;
			setHighMem();
			isMembers = true;
			Signlink.storeid = 32;
			Signlink.startpriv(InetAddress.getLocalHost());
			initClientFrame(frameWidth, frameHeight);
			instance = this;
		} catch (Exception exception) {
			return;
		}
	}

	public void startRunnable(Runnable runnable, int i) {
		if (i > 10)
			i = 10;
		if (Signlink.mainapp != null) {
			Signlink.startthread(runnable, i);
		} else {
			super.startRunnable(runnable, i);
		}
	}

	public Socket openSocket(int port) throws IOException {
		return new Socket(InetAddress.getByName(server), port);
	}

	private void processMenuClick() {
		if (activeInterfaceType != 0)
			return;
		int j = super.clickMode3;
		if (spellSelected == 1 && super.saveClickX >= 516
				&& super.saveClickY >= 160 && super.saveClickX <= 765
				&& super.saveClickY <= 205)
			j = 0;
		if (menuOpen) {
			if (j != 1) {
				int k = super.mouseX;
				int j1 = super.mouseY;
				if (menuScreenArea == 0) {
					k -= 4;
					j1 -= 4;
				}
				if (menuScreenArea == 1) {
					k -= 519;
					j1 -= 168;
				}
				if (menuScreenArea == 2) {
					k -= 17;
					j1 -= 338;
				}
				if (menuScreenArea == 3) {
					k -= 519;
					j1 -= 0;
				}
				if (k < menuOffsetX - 10 || k > menuOffsetX + menuWidth + 10
						|| j1 < menuOffsetY - 10
						|| j1 > menuOffsetY + menuHeight + 10) {
					menuOpen = false;
					if (menuScreenArea == 1) {
					}
					if (menuScreenArea == 2)
						inputTaken = true;
				}
			}
			if (j == 1) {
				int l = menuOffsetX;
				int k1 = menuOffsetY;
				int i2 = menuWidth;
				int k2 = super.saveClickX;
				int l2 = super.saveClickY;
				switch (menuScreenArea) {
				case 0:
					k2 -= 4;
					l2 -= 4;
					break;
				case 1:
					k2 -= 519;
					l2 -= 168;
					break;
				case 2:
					k2 -= 5;
					l2 -= 338;
					break;
				case 3:
					k2 -= 519;
					l2 -= 0;
					break;
				}
				int i3 = -1;
				for (int j3 = 0; j3 < menuActionRow; j3++) {
					int k3 = k1 + 31 + (menuActionRow - 1 - j3) * 15;
					if (k2 > l && k2 < l + i2 && l2 > k3 - 13 && l2 < k3 + 3)
						i3 = j3;
				}
				if (i3 != -1)
					doAction(i3);
				menuOpen = false;
				if (menuScreenArea == 1) {
				}
				if (menuScreenArea == 2) {
					inputTaken = true;
				}
			}
		} else {
			if (j == 1 && menuActionRow > 0) {
				int i1 = menuActionID[menuActionRow - 1];
				if (i1 == 632 || i1 == 78 || i1 == 867 || i1 == 431 || i1 == 53
						|| i1 == 74 || i1 == 454 || i1 == 539 || i1 == 493
						|| i1 == 847 || i1 == 447 || i1 == 1125) {
					int l1 = menuActionCmd2[menuActionRow - 1];
					int j2 = menuActionCmd3[menuActionRow - 1];
					Widget class9 = Widget.interfaceCache[j2];
					if (class9.aBoolean259 || class9.replaceItems) {
						aBoolean1242 = false;
						anInt989 = 0;
						anInt1084 = j2;
						anInt1085 = l1;
						activeInterfaceType = 2;
						anInt1087 = super.saveClickX;
						anInt1088 = super.saveClickY;
						if (Widget.interfaceCache[j2].parent == openInterfaceId)
							activeInterfaceType = 1;
						if (Widget.interfaceCache[j2].parent == backDialogueId)
							activeInterfaceType = 3;
						return;
					}
				}
			}
			if (j == 1
					&& (anInt1253 == 1 || menuHasAddFriend(menuActionRow - 1))
					&& menuActionRow > 2)
				j = 2;
			if (j == 1 && menuActionRow > 0)
				doAction(menuActionRow - 1);
			if (j == 2 && menuActionRow > 0)
				determineMenuSize();
			processMainScreenClick();
			processTabClick();
			processChatModeClick();
			minimapHovers();
		}
	}

	public static String getFileNameWithoutExtension(String fileName) {
		File tmpFile = new File(fileName);
		tmpFile.getName();
		int whereDot = tmpFile.getName().lastIndexOf('.');
		if (0 < whereDot && whereDot <= tmpFile.getName().length() - 2) {
			return tmpFile.getName().substring(0, whereDot);
		}
		return "";
	}

	public void preloadModels() {
		File file = new File(Signlink.findcachedir() + "Raw/");
		File[] fileArray = file.listFiles();
		for (int y = 0; y < fileArray.length; y++) {
			String s = fileArray[y].getName();
			byte[] buffer = ReadFile(Signlink.findcachedir() + "Raw/" + s);
			Model.method460(buffer,
					Integer.parseInt(getFileNameWithoutExtension(s)));
		}
	}

	private void saveMidi(boolean flag, byte abyte0[]) {
		Signlink.fadeMidi = flag ? 1 : 0;
		Signlink.saveMidi(abyte0, abyte0.length);
	}

	private void updateWorldObjects() {
		try {
			anInt985 = -1;
			incompleteAnimables.clear();
			projectiles.clear();
			Rasterizer.method366();
			unlinkMRUNodes();
			worldController.initToNull();
			System.gc();
			for (int i = 0; i < 4; i++)
				aClass11Array1230[i].initialize();
			for (int l = 0; l < 4; l++) {
				for (int k1 = 0; k1 < 104; k1++) {
					for (int j2 = 0; j2 < 104; j2++)
						byteGroundArray[l][k1][j2] = 0;
				}
			}

			ObjectManager objectManager = new ObjectManager(byteGroundArray,
					intGroundArray);
			int k2 = aByteArrayArray1183.length;
			outgoing.createFrame(0);
			if (!aBoolean1159) {
				for (int i3 = 0; i3 < k2; i3++) {
					int i4 = (anIntArray1234[i3] >> 8) * 64 - baseX;
					int k5 = (anIntArray1234[i3] & 0xff) * 64 - baseY;
					byte abyte0[] = aByteArrayArray1183[i3];
					if (abyte0 != null)
						objectManager.method180(abyte0, k5, i4,
								(anInt1069 - 6) * 8, (anInt1070 - 6) * 8,
								aClass11Array1230);
				}
				for (int j4 = 0; j4 < k2; j4++) {
					int l5 = (anIntArray1234[j4] >> 8) * 64 - baseX;
					int k7 = (anIntArray1234[j4] & 0xff) * 64 - baseY;
					byte abyte2[] = aByteArrayArray1183[j4];
					if (abyte2 == null && anInt1070 < 800)
						objectManager.method174(k7, 64, 64, l5);
				}
				anInt1097++;
				if (anInt1097 > 160) {
					anInt1097 = 0;
					outgoing.createFrame(238);
					outgoing.writeByte(96);
				}
				outgoing.createFrame(0);
				for (int i6 = 0; i6 < k2; i6++) {
					byte abyte1[] = aByteArrayArray1247[i6];
					if (abyte1 != null) {
						int l8 = (anIntArray1234[i6] >> 8) * 64 - baseX;
						int k9 = (anIntArray1234[i6] & 0xff) * 64 - baseY;
						objectManager.method190(l8, aClass11Array1230, k9,
								worldController, abyte1);
					}
				}

			}
			if (aBoolean1159) {
				for (int j3 = 0; j3 < 4; j3++) {
					for (int k4 = 0; k4 < 13; k4++) {
						for (int j6 = 0; j6 < 13; j6++) {
							int l7 = anIntArrayArrayArray1129[j3][k4][j6];
							if (l7 != -1) {
								int i9 = l7 >> 24 & 3;
								int l9 = l7 >> 1 & 3;
								int j10 = l7 >> 14 & 0x3ff;
								int l10 = l7 >> 3 & 0x7ff;
								int j11 = (j10 / 8 << 8) + l10 / 8;
								for (int l11 = 0; l11 < anIntArray1234.length; l11++) {
									if (anIntArray1234[l11] != j11
											|| aByteArrayArray1183[l11] == null)
										continue;
									objectManager.method179(i9, l9,
											aClass11Array1230, k4 * 8,
											(j10 & 7) * 8,
											aByteArrayArray1183[l11],
											(l10 & 7) * 8, j3, j6 * 8);
									break;
								}

							}
						}
					}
				}
				for (int l4 = 0; l4 < 13; l4++) {
					for (int k6 = 0; k6 < 13; k6++) {
						int i8 = anIntArrayArrayArray1129[0][l4][k6];
						if (i8 == -1)
							objectManager.method174(k6 * 8, 8, 8, l4 * 8);
					}
				}

				outgoing.createFrame(0);
				for (int l6 = 0; l6 < 4; l6++) {
					for (int j8 = 0; j8 < 13; j8++) {
						for (int j9 = 0; j9 < 13; j9++) {
							int i10 = anIntArrayArrayArray1129[l6][j8][j9];
							if (i10 != -1) {
								int k10 = i10 >> 24 & 3;
								int i11 = i10 >> 1 & 3;
								int k11 = i10 >> 14 & 0x3ff;
								int i12 = i10 >> 3 & 0x7ff;
								int j12 = (k11 / 8 << 8) + i12 / 8;
								for (int k12 = 0; k12 < anIntArray1234.length; k12++) {
									if (anIntArray1234[k12] != j12
											|| aByteArrayArray1247[k12] == null)
										continue;
									objectManager.method183(aClass11Array1230,
											worldController, k10, j8 * 8,
											(i12 & 7) * 8, l6,
											aByteArrayArray1247[k12],
											(k11 & 7) * 8, i11, j9 * 8);
									break;
								}

							}
						}

					}

				}

			}
			outgoing.createFrame(0);
			objectManager.method171(aClass11Array1230, worldController);
			gameScreenImageProducer.initDrawingArea();
			outgoing.createFrame(0);
			int k3 = ObjectManager.anInt145;
			if (k3 > plane)
				k3 = plane;
			if (k3 < plane - 1)
				k3 = plane - 1;
			if (lowMem)
				worldController.method275(ObjectManager.anInt145);
			else
				worldController.method275(0);
			for (int i5 = 0; i5 < 104; i5++) {
				for (int i7 = 0; i7 < 104; i7++)
					spawnGroundItem(i5, i7);

			}

			anInt1051++;
			if (anInt1051 > 98) {
				anInt1051 = 0;
				outgoing.createFrame(150);
			}
			method63();
		} catch (Exception exception) {
		}
		ObjectDefinition.mruNodes1.unlinkAll();
		if (super.gameFrame != null) {
			outgoing.createFrame(210);
			outgoing.writeInt(0x3f008edd);
		}
		if (lowMem && Signlink.cache_dat != null) {
			int j = onDemandFetcher.getVersionCount(0);
			for (int i1 = 0; i1 < j; i1++) {
				int l1 = onDemandFetcher.getModelIndex(i1);
				if ((l1 & 0x79) == 0)
					Model.method461(i1);
			}

		}
		System.gc();
		Rasterizer.method367();
		onDemandFetcher.method566();
		int k = (anInt1069 - 6) / 8 - 1;
		int j1 = (anInt1069 + 6) / 8 + 1;
		int i2 = (anInt1070 - 6) / 8 - 1;
		int l2 = (anInt1070 + 6) / 8 + 1;
		if (aBoolean1141) {
			k = 49;
			j1 = 50;
			i2 = 49;
			l2 = 50;
		}
		for (int l3 = k; l3 <= j1; l3++) {
			for (int j5 = i2; j5 <= l2; j5++)
				if (l3 == k || l3 == j1 || j5 == i2 || j5 == l2) {
					int j7 = onDemandFetcher.method562(0, j5, l3);
					if (j7 != -1)
						onDemandFetcher.method560(j7, 3);
					int k8 = onDemandFetcher.method562(1, j5, l3);
					if (k8 != -1)
						onDemandFetcher.method560(k8, 3);
				}

		}

	}

	public static AbstractMap.SimpleEntry<Integer, Integer> getNextInteger(
			ArrayList<Integer> values) {
		ArrayList<AbstractMap.SimpleEntry<Integer, Integer>> frequencies = new ArrayList<>();
		int maxIndex = 0;
		main: for (int i = 0; i < values.size(); ++i) {
			int value = values.get(i);
			for (int j = 0; j < frequencies.size(); ++j) {
				if (frequencies.get(j).getKey() == value) {
					frequencies.get(j).setValue(
							frequencies.get(j).getValue() + 1);
					if (frequencies.get(maxIndex).getValue() < frequencies.get(
							j).getValue()) {
						maxIndex = j;
					}
					continue main;
				}
			}
			frequencies.add(new AbstractMap.SimpleEntry<Integer, Integer>(
					value, 1));
		}
		return frequencies.get(maxIndex);
	}

	private void unlinkMRUNodes() {
		ObjectDefinition.mruNodes1.unlinkAll();
		ObjectDefinition.mruNodes2.unlinkAll();
		NpcDefinition.modelCache.unlinkAll();
		ItemDefinition.model_cache.unlinkAll();
		ItemDefinition.image_cache.unlinkAll();
		Player.mruNodes.unlinkAll();
		SpotAnimation.memCache.unlinkAll();
	}

	private void renderMapScene(int i) {
		int ai[] = minimapImage.myPixels;
		int j = ai.length;
		for (int k = 0; k < j; k++)
			ai[k] = 0;

		for (int l = 1; l < 103; l++) {
			int i1 = 24628 + (103 - l) * 512 * 4;
			for (int k1 = 1; k1 < 103; k1++) {
				if ((byteGroundArray[i][k1][l] & 0x18) == 0)
					worldController.method309(ai, i1, i, k1, l);
				if (i < 3 && (byteGroundArray[i + 1][k1][l] & 8) != 0)
					worldController.method309(ai, i1, i + 1, k1, l);
				i1 += 4;
			}

		}

		int j1 = 0xFFFFFF;
		int l1 = 0xEE0000;
		minimapImage.method343();
		for (int i2 = 1; i2 < 103; i2++) {
			for (int j2 = 1; j2 < 103; j2++) {
				if ((byteGroundArray[i][j2][i2] & 0x18) == 0)
					drawMapScenes(i2, j1, j2, l1, i);
				if (i < 3 && (byteGroundArray[i + 1][j2][i2] & 8) != 0)
					drawMapScenes(i2, j1, j2, l1, i + 1);
			}

		}

		gameScreenImageProducer.initDrawingArea();
		anInt1071 = 0;
		for (int k2 = 0; k2 < 104; k2++) {
			for (int l2 = 0; l2 < 104; l2++) {
				int i3 = worldController.method303(plane, k2, l2);
				if (i3 != 0) {
					i3 = i3 >> 14 & 0x7fff;
					int j3 = ObjectDefinition.lookup(i3).minimapFunction;
					if (j3 >= 0) {
						int k3 = k2;
						int l3 = l2;
						minimapHint[anInt1071] = mapFunctions[j3];
						minimapHintX[anInt1071] = k3;
						minimapHintY[anInt1071] = l3;
						anInt1071++;
					}
				}
			}

		}

	}

	private void spawnGroundItem(int i, int j) {
		Deque class19 = groundItems[plane][i][j];
		if (class19 == null) {
			worldController.method295(plane, i, j);
			return;
		}
		int k = 0xfa0a1f01;
		Object obj = null;
		for (Item item = (Item) class19.reverseGetFirst(); item != null; item = (Item) class19
				.reverseGetNext()) {
			ItemDefinition itemDef = ItemDefinition.lookup(item.ID);
			int l = itemDef.value;
			if (itemDef.stackable)
				l *= item.anInt1559 + 1;
			// notifyItemSpawn(item, i + baseX, j + baseY);

			if (l > k) {
				k = l;
				obj = item;
			}
		}

		class19.insertTail(((Linkable) (obj)));
		Object obj1 = null;
		Object obj2 = null;
		for (Item class30_sub2_sub4_sub2_1 = (Item) class19.reverseGetFirst(); class30_sub2_sub4_sub2_1 != null; class30_sub2_sub4_sub2_1 = (Item) class19
				.reverseGetNext()) {
			if (class30_sub2_sub4_sub2_1.ID != ((Item) (obj)).ID
					&& obj1 == null)
				obj1 = class30_sub2_sub4_sub2_1;
			if (class30_sub2_sub4_sub2_1.ID != ((Item) (obj)).ID
					&& class30_sub2_sub4_sub2_1.ID != ((Item) (obj1)).ID
					&& obj2 == null)
				obj2 = class30_sub2_sub4_sub2_1;
		}

		int i1 = i + (j << 7) + 0x60000000;
		worldController.method281(i, i1, ((Renderable) (obj1)),
				method42(plane, j * 128 + 64, i * 128 + 64),
				((Renderable) (obj2)), ((Renderable) (obj)), plane, j);
	}

	private void showNPCs(boolean flag) {
		for (int j = 0; j < npcCount; j++) {
			Npc npc = npcs[npcIndices[j]];
			int k = 0x20000000 + (npcIndices[j] << 14);
			if (npc == null || !npc.isVisible()
					|| npc.desc.priorityRender != flag)
				continue;
			int l = npc.x >> 7;
			int i1 = npc.y >> 7;
			if (l < 0 || l >= 104 || i1 < 0 || i1 >= 104)
				continue;
			if (npc.boundDim == 1 && (npc.x & 0x7f) == 64
					&& (npc.y & 0x7f) == 64) {
				if (anIntArrayArray929[l][i1] == anInt1265)
					continue;
				anIntArrayArray929[l][i1] = anInt1265;
			}
			if (!npc.desc.clickable)
				k += 0x80000000;
			worldController.method285(plane, npc.anInt1552,
					method42(plane, npc.y, npc.x), k, npc.y,
					(npc.boundDim - 1) * 64 + 60, npc.x, npc, npc.aBoolean1541);
		}
	}

	public void drawHoverBox(int xPos, int yPos, String text) {
		String[] results = text.split("\n");
		int height = (results.length * 16) + 6;
		int width;
		width = smallText.getTextWidth(results[0]) + 6;
		for (int i = 1; i < results.length; i++)
			if (width <= smallText.getTextWidth(results[i]) + 6)
				width = smallText.getTextWidth(results[i]) + 6;
		Raster.drawPixels(height, yPos, xPos, 0xFFFFA0, width);
		Raster.fillPixels(xPos, width, height, 0, yPos);
		yPos += 14;
		for (int i = 0; i < results.length; i++) {
			smallText.drawTextWithPotentialShadow(false, xPos + 3, 0,
					results[i], yPos);
			yPos += 16;
		}
	}

	private void buildInterfaceMenu(int i, Widget widget, int k, int l, int i1,
			int j1) {
		if (widget == null)
			widget = Widget.interfaceCache[21356];
		if (widget.type != 0 || widget.children == null || widget.hoverOnly)
			return;
		if (k < i || i1 < l || k > i + widget.width || i1 > l + widget.height)
			return;
		int k1 = widget.children.length;
		for (int l1 = 0; l1 < k1; l1++) {
			int i2 = widget.childX[l1] + i;
			int j2 = (widget.childY[l1] + l) - j1;
			Widget childInterface = Widget.interfaceCache[widget.children[l1]];
			i2 += childInterface.x;
			j2 += childInterface.anInt265;
			if ((childInterface.hoverType >= 0 || childInterface.defaultHoverColor != 0)
					&& k >= i2
					&& i1 >= j2
					&& k < i2 + childInterface.width
					&& i1 < j2 + childInterface.height)
				if (childInterface.hoverType >= 0)
					anInt886 = childInterface.hoverType;
				else
					anInt886 = childInterface.id;
			if (childInterface.type == 8 && k >= i2 && i1 >= j2
					&& k < i2 + childInterface.width
					&& i1 < j2 + childInterface.height) {
				anInt1315 = childInterface.id;
			}
			if (childInterface.type == Widget.TYPE_CONTAINER) {
				buildInterfaceMenu(i2, childInterface, k, j2, i1,
						childInterface.scrollPosition);
				if (childInterface.scrollMax > childInterface.height)
					method65(i2 + childInterface.width, childInterface.height,
							k, i1, childInterface, j2, true,
							childInterface.scrollMax);
			} else {
				if (childInterface.optionType == Widget.OPTION_OK && k >= i2 && i1 >= j2
						&& k < i2 + childInterface.width
						&& i1 < j2 + childInterface.height) {
					boolean flag = false;
					if (childInterface.contentType != 0)
						flag = buildFriendsListMenu(childInterface);
					if (!flag) {
						menuActionName[menuActionRow] = childInterface.tooltip;
						menuActionID[menuActionRow] = 315;
						menuActionCmd3[menuActionRow] = childInterface.id;
						menuActionRow++;
					}
				}
				if (childInterface.optionType == Widget.OPTION_USABLE && spellSelected == 0
						&& k >= i2 && i1 >= j2 && k < i2 + childInterface.width
						&& i1 < j2 + childInterface.height) {
					String s = childInterface.selectedActionName;
					if (s.indexOf(" ") != -1)
						s = s.substring(0, s.indexOf(" "));
					if (childInterface.spellName.endsWith("Rush")
							|| childInterface.spellName.endsWith("Burst")
							|| childInterface.spellName.endsWith("Blitz")
							|| childInterface.spellName.endsWith("Barrage")
							|| childInterface.spellName.endsWith("strike")
							|| childInterface.spellName.endsWith("bolt")
							|| childInterface.spellName
									.equals("Crumble undead")
							|| childInterface.spellName.endsWith("blast")
							|| childInterface.spellName.endsWith("wave")
							|| childInterface.spellName
									.equals("Claws of Guthix")
							|| childInterface.spellName
									.equals("Flames of Zamorak")
							|| childInterface.spellName.equals("Magic Dart")) {
						menuActionName[menuActionRow] = "Autocast @gre@"
								+ childInterface.spellName;
						menuActionID[menuActionRow] = 104;
						menuActionCmd3[menuActionRow] = childInterface.id;
						menuActionRow++;
					}
					menuActionName[menuActionRow] = s + " @gre@"
							+ childInterface.spellName;
					menuActionID[menuActionRow] = 626;
					menuActionCmd3[menuActionRow] = childInterface.id;
					menuActionRow++;
				}
				if (childInterface.optionType == Widget.OPTION_CLOSE && k >= i2 && i1 >= j2
						&& k < i2 + childInterface.width
						&& i1 < j2 + childInterface.height) {
					menuActionName[menuActionRow] = "Close";
					menuActionID[menuActionRow] = 200;
					menuActionCmd3[menuActionRow] = childInterface.id;
					menuActionRow++;
				}
				if (childInterface.optionType == Widget.OPTION_TOGGLE_SETTING && k >= i2 && i1 >= j2
						&& k < i2 + childInterface.width
						&& i1 < j2 + childInterface.height) {
					// System.out.println("2"+class9_1.tooltip + ", " +
					// class9_1.interfaceID);
					// menuActionName[menuActionRow] = class9_1.tooltip + ", " +
					// class9_1.id;
					menuActionName[menuActionRow] = childInterface.tooltip;
					menuActionID[menuActionRow] = 169;
					menuActionCmd3[menuActionRow] = childInterface.id;
					menuActionRow++;
					if (childInterface.hoverText != null) {
						// drawHoverBox(k, l, class9_1.hoverText);
						// System.out.println("DRAWING INTERFACE: " +
						// class9_1.hoverText);
					}
				}
				if (childInterface.optionType == Widget.OPTION_RESET_SETTING && k >= i2 && i1 >= j2
						&& k < i2 + childInterface.width
						&& i1 < j2 + childInterface.height) {
					// System.out.println("3"+class9_1.tooltip + ", " +
					// class9_1.interfaceID);
					// menuActionName[menuActionRow] = class9_1.tooltip + ", " +
					// class9_1.id;
					menuActionName[menuActionRow] = childInterface.tooltip;
					menuActionID[menuActionRow] = 646;
					menuActionCmd3[menuActionRow] = childInterface.id;
					menuActionRow++;
				}
				if (childInterface.optionType == Widget.OPTION_CONTINUE && !continuedDialogue
						&& k >= i2 && i1 >= j2 && k < i2 + childInterface.width
						&& i1 < j2 + childInterface.height) {
					// System.out.println("4"+class9_1.tooltip + ", " +
					// class9_1.interfaceID);
					// menuActionName[menuActionRow] = class9_1.tooltip + ", " +
					// class9_1.id;
					menuActionName[menuActionRow] = childInterface.tooltip;
					menuActionID[menuActionRow] = 679;
					menuActionCmd3[menuActionRow] = childInterface.id;
					menuActionRow++;
				}
				if (childInterface.type == Widget.TYPE_INVENTORY) {
					int k2 = 0;
					for (int l2 = 0; l2 < childInterface.height; l2++) {
						for (int i3 = 0; i3 < childInterface.width; i3++) {
							int j3 = i2 + i3
									* (32 + childInterface.spritePaddingX);
							int k3 = j2 + l2
									* (32 + childInterface.spritePaddingY);
							if (k2 < 20) {
								j3 += childInterface.spritesX[k2];
								k3 += childInterface.spritesY[k2];
							}
							if (k >= j3 && i1 >= k3 && k < j3 + 32
									&& i1 < k3 + 32) {
								mouseInvInterfaceIndex = k2;
								lastActiveInvInterface = childInterface.id;
								if (childInterface.inventoryItemId[k2] > 0) {
									ItemDefinition itemDef = ItemDefinition
											.lookup(childInterface.inventoryItemId[k2] - 1);
									if (itemSelected == 1
											&& childInterface.hasActions) {
										if (childInterface.id != anInt1284
												|| k2 != anInt1283) {
											menuActionName[menuActionRow] = "Use "
													+ selectedItemName
													+ " with @lre@"
													+ itemDef.name;
											menuActionID[menuActionRow] = 870;
											menuActionCmd1[menuActionRow] = itemDef.id;
											menuActionCmd2[menuActionRow] = k2;
											menuActionCmd3[menuActionRow] = childInterface.id;
											menuActionRow++;
										}
									} else if (spellSelected == 1
											&& childInterface.hasActions) {
										if ((spellUsableOn & 0x10) == 16) {
											menuActionName[menuActionRow] = spellTooltip
													+ " @lre@" + itemDef.name;
											menuActionID[menuActionRow] = 543;
											menuActionCmd1[menuActionRow] = itemDef.id;
											menuActionCmd2[menuActionRow] = k2;
											menuActionCmd3[menuActionRow] = childInterface.id;
											menuActionRow++;
										}
									} else {
										if (childInterface.hasActions) {
											for (int l3 = 4; l3 >= 3; l3--)
												if (itemDef.actions != null
														&& itemDef.actions[l3] != null) {
													menuActionName[menuActionRow] = itemDef.actions[l3]
															+ " @lre@"
															+ itemDef.name;
													if (l3 == 3)
														menuActionID[menuActionRow] = 493;
													if (l3 == 4)
														menuActionID[menuActionRow] = 847;
													menuActionCmd1[menuActionRow] = itemDef.id;
													menuActionCmd2[menuActionRow] = k2;
													menuActionCmd3[menuActionRow] = childInterface.id;
													menuActionRow++;
												} else if (l3 == 4) {
													menuActionName[menuActionRow] = "Drop @lre@"
															+ itemDef.name;
													menuActionID[menuActionRow] = 847;
													menuActionCmd1[menuActionRow] = itemDef.id;
													menuActionCmd2[menuActionRow] = k2;
													menuActionCmd3[menuActionRow] = childInterface.id;
													menuActionRow++;
												}
										}
										if (childInterface.usableItems) {
											menuActionName[menuActionRow] = "Use @lre@"
													+ itemDef.name;
											menuActionID[menuActionRow] = 447;
											menuActionCmd1[menuActionRow] = itemDef.id;
											menuActionCmd2[menuActionRow] = k2;
											menuActionCmd3[menuActionRow] = childInterface.id;
											menuActionRow++;
										}
										if (childInterface.hasActions
												&& itemDef.actions != null) {
											for (int i4 = 2; i4 >= 0; i4--)
												if (itemDef.actions[i4] != null) {
													menuActionName[menuActionRow] = itemDef.actions[i4]
															+ " @lre@"
															+ itemDef.name;
													if (i4 == 0)
														menuActionID[menuActionRow] = 74;
													if (i4 == 1)
														menuActionID[menuActionRow] = 454;
													if (i4 == 2)
														menuActionID[menuActionRow] = 539;
													menuActionCmd1[menuActionRow] = itemDef.id;
													menuActionCmd2[menuActionRow] = k2;
													menuActionCmd3[menuActionRow] = childInterface.id;
													menuActionRow++;
												}

										}
										if (childInterface.actions != null) {
											for (int j4 = 4; j4 >= 0; j4--)
												if (childInterface.actions[j4] != null) {
													menuActionName[menuActionRow] = childInterface.actions[j4]
															+ " @lre@"
															+ itemDef.name;
													if (j4 == 0)
														menuActionID[menuActionRow] = 632;
													if (j4 == 1)
														menuActionID[menuActionRow] = 78;
													if (j4 == 2)
														menuActionID[menuActionRow] = 867;
													if (j4 == 3)
														menuActionID[menuActionRow] = 431;
													if (j4 == 4)
														menuActionID[menuActionRow] = 53;
													menuActionCmd1[menuActionRow] = itemDef.id;
													menuActionCmd2[menuActionRow] = k2;
													menuActionCmd3[menuActionRow] = childInterface.id;
													menuActionRow++;
												}

										}
										if (Configuration.enableIds
												&& (myPrivilege >= 2 && myPrivilege <= 3)) {
											menuActionName[menuActionRow] = "Examine @lre@"
													+ itemDef.name
													+ " @gre@(@whi@"
													+ (childInterface.inventoryItemId[k2] - 1)
													+ "@gre@)";
										} else {
											menuActionName[menuActionRow] = "Examine @lre@"
													+ itemDef.name;
										}
										menuActionID[menuActionRow] = 1125;
										menuActionCmd1[menuActionRow] = itemDef.id;
										menuActionCmd2[menuActionRow] = k2;
										menuActionCmd3[menuActionRow] = childInterface.id;
										menuActionRow++;
									}
								}
							}
							k2++;
						}
					}
				}
			}
		}
	}

	public void drawTransparentScrollBar(int x, int y, int height,
			int maxScroll, int pos) {
		cacheSprite[29].drawARGBSprite(x, y, 120);
		cacheSprite[30].drawARGBSprite(x, y + height - 16, 120);
		Raster.drawVerticalLine(x, y + 16, height - 32, 0xffffff, 64);
		Raster.drawVerticalLine(x + 15, y + 16, height - 32, 0xffffff, 64);
		int barHeight = (height - 32) * height / maxScroll;
		if (barHeight < 10) {
			barHeight = 10;
		}
		int barPos = 0;
		if (maxScroll != height) {
			barPos = (height - 32 - barHeight) * pos / (maxScroll - height);
		}
		Raster.drawRectangle(x, y + 16 + barPos, 16, 5 + y + 16 + barPos
				+ barHeight - 5 - (y + 16 + barPos), 0xffffff, 32);
	}

	public void drawScrollbar(int height, int pos, int y, int x, int maxScroll,
			boolean transparent) {
		if (transparent) {
			drawTransparentScrollBar(x, y, height, maxScroll, pos);
		} else {
			scrollBar1.drawSprite(x, y);
			scrollBar2.drawSprite(x, (y + height) - 16);
			Raster.drawPixels(height - 32, y + 16, x, 0x000001, 16);
			Raster.drawPixels(height - 32, y + 16, x, 0x3d3426, 15);
			Raster.drawPixels(height - 32, y + 16, x, 0x342d21, 13);
			Raster.drawPixels(height - 32, y + 16, x, 0x2e281d, 11);
			Raster.drawPixels(height - 32, y + 16, x, 0x29241b, 10);
			Raster.drawPixels(height - 32, y + 16, x, 0x252019, 9);
			Raster.drawPixels(height - 32, y + 16, x, 0x000001, 1);
			int k1 = ((height - 32) * height) / maxScroll;
			if (k1 < 8) {
				k1 = 8;
			}
			int l1 = ((height - 32 - k1) * pos) / (maxScroll - height);
			Raster.drawPixels(k1, y + 16 + l1, x, barFillColor, 16);
			Raster.method341(y + 16 + l1, 0x000001, k1, x);
			Raster.method341(y + 16 + l1, 0x817051, k1, x + 1);
			Raster.method341(y + 16 + l1, 0x73654a, k1, x + 2);
			Raster.method341(y + 16 + l1, 0x6a5c43, k1, x + 3);
			Raster.method341(y + 16 + l1, 0x6a5c43, k1, x + 4);
			Raster.method341(y + 16 + l1, 0x655841, k1, x + 5);
			Raster.method341(y + 16 + l1, 0x655841, k1, x + 6);
			Raster.method341(y + 16 + l1, 0x61553e, k1, x + 7);
			Raster.method341(y + 16 + l1, 0x61553e, k1, x + 8);
			Raster.method341(y + 16 + l1, 0x5d513c, k1, x + 9);
			Raster.method341(y + 16 + l1, 0x5d513c, k1, x + 10);
			Raster.method341(y + 16 + l1, 0x594e3a, k1, x + 11);
			Raster.method341(y + 16 + l1, 0x594e3a, k1, x + 12);
			Raster.method341(y + 16 + l1, 0x514635, k1, x + 13);
			Raster.method341(y + 16 + l1, 0x4b4131, k1, x + 14);
			Raster.method339(y + 16 + l1, 0x000001, 15, x);
			Raster.method339(y + 17 + l1, 0x000001, 15, x);
			Raster.method339(y + 17 + l1, 0x655841, 14, x);
			Raster.method339(y + 17 + l1, 0x6a5c43, 13, x);
			Raster.method339(y + 17 + l1, 0x6d5f48, 11, x);
			Raster.method339(y + 17 + l1, 0x73654a, 10, x);
			Raster.method339(y + 17 + l1, 0x76684b, 7, x);
			Raster.method339(y + 17 + l1, 0x7b6a4d, 5, x);
			Raster.method339(y + 17 + l1, 0x7e6e50, 4, x);
			Raster.method339(y + 17 + l1, 0x817051, 3, x);
			Raster.method339(y + 17 + l1, 0x000001, 2, x);
			Raster.method339(y + 18 + l1, 0x000001, 16, x);
			Raster.method339(y + 18 + l1, 0x564b38, 15, x);
			Raster.method339(y + 18 + l1, 0x5d513c, 14, x);
			Raster.method339(y + 18 + l1, 0x625640, 11, x);
			Raster.method339(y + 18 + l1, 0x655841, 10, x);
			Raster.method339(y + 18 + l1, 0x6a5c43, 7, x);
			Raster.method339(y + 18 + l1, 0x6e6046, 5, x);
			Raster.method339(y + 18 + l1, 0x716247, 4, x);
			Raster.method339(y + 18 + l1, 0x7b6a4d, 3, x);
			Raster.method339(y + 18 + l1, 0x817051, 2, x);
			Raster.method339(y + 18 + l1, 0x000001, 1, x);
			Raster.method339(y + 19 + l1, 0x000001, 16, x);
			Raster.method339(y + 19 + l1, 0x514635, 15, x);
			Raster.method339(y + 19 + l1, 0x564b38, 14, x);
			Raster.method339(y + 19 + l1, 0x5d513c, 11, x);
			Raster.method339(y + 19 + l1, 0x61553e, 9, x);
			Raster.method339(y + 19 + l1, 0x655841, 7, x);
			Raster.method339(y + 19 + l1, 0x6a5c43, 5, x);
			Raster.method339(y + 19 + l1, 0x6e6046, 4, x);
			Raster.method339(y + 19 + l1, 0x73654a, 3, x);
			Raster.method339(y + 19 + l1, 0x817051, 2, x);
			Raster.method339(y + 19 + l1, 0x000001, 1, x);
			Raster.method339(y + 20 + l1, 0x000001, 16, x);
			Raster.method339(y + 20 + l1, 0x4b4131, 15, x);
			Raster.method339(y + 20 + l1, 0x544936, 14, x);
			Raster.method339(y + 20 + l1, 0x594e3a, 13, x);
			Raster.method339(y + 20 + l1, 0x5d513c, 10, x);
			Raster.method339(y + 20 + l1, 0x61553e, 8, x);
			Raster.method339(y + 20 + l1, 0x655841, 6, x);
			Raster.method339(y + 20 + l1, 0x6a5c43, 4, x);
			Raster.method339(y + 20 + l1, 0x73654a, 3, x);
			Raster.method339(y + 20 + l1, 0x817051, 2, x);
			Raster.method339(y + 20 + l1, 0x000001, 1, x);
			Raster.method341(y + 16 + l1, 0x000001, k1, x + 15);
			Raster.method339(y + 15 + l1 + k1, 0x000001, 16, x);
			Raster.method339(y + 14 + l1 + k1, 0x000001, 15, x);
			Raster.method339(y + 14 + l1 + k1, 0x3f372a, 14, x);
			Raster.method339(y + 14 + l1 + k1, 0x443c2d, 10, x);
			Raster.method339(y + 14 + l1 + k1, 0x483e2f, 9, x);
			Raster.method339(y + 14 + l1 + k1, 0x4a402f, 7, x);
			Raster.method339(y + 14 + l1 + k1, 0x4b4131, 4, x);
			Raster.method339(y + 14 + l1 + k1, 0x564b38, 3, x);
			Raster.method339(y + 14 + l1 + k1, 0x000001, 2, x);
			Raster.method339(y + 13 + l1 + k1, 0x000001, 16, x);
			Raster.method339(y + 13 + l1 + k1, 0x443c2d, 15, x);
			Raster.method339(y + 13 + l1 + k1, 0x4b4131, 11, x);
			Raster.method339(y + 13 + l1 + k1, 0x514635, 9, x);
			Raster.method339(y + 13 + l1 + k1, 0x544936, 7, x);
			Raster.method339(y + 13 + l1 + k1, 0x564b38, 6, x);
			Raster.method339(y + 13 + l1 + k1, 0x594e3a, 4, x);
			Raster.method339(y + 13 + l1 + k1, 0x625640, 3, x);
			Raster.method339(y + 13 + l1 + k1, 0x6a5c43, 2, x);
			Raster.method339(y + 13 + l1 + k1, 0x000001, 1, x);
			Raster.method339(y + 12 + l1 + k1, 0x000001, 16, x);
			Raster.method339(y + 12 + l1 + k1, 0x443c2d, 15, x);
			Raster.method339(y + 12 + l1 + k1, 0x4b4131, 14, x);
			Raster.method339(y + 12 + l1 + k1, 0x544936, 12, x);
			Raster.method339(y + 12 + l1 + k1, 0x564b38, 11, x);
			Raster.method339(y + 12 + l1 + k1, 0x594e3a, 10, x);
			Raster.method339(y + 12 + l1 + k1, 0x5d513c, 7, x);
			Raster.method339(y + 12 + l1 + k1, 0x61553e, 4, x);
			Raster.method339(y + 12 + l1 + k1, 0x6e6046, 3, x);
			Raster.method339(y + 12 + l1 + k1, 0x7b6a4d, 2, x);
			Raster.method339(y + 12 + l1 + k1, 0x000001, 1, x);
			Raster.method339(y + 11 + l1 + k1, 0x000001, 16, x);
			Raster.method339(y + 11 + l1 + k1, 0x4b4131, 15, x);
			Raster.method339(y + 11 + l1 + k1, 0x514635, 14, x);
			Raster.method339(y + 11 + l1 + k1, 0x564b38, 13, x);
			Raster.method339(y + 11 + l1 + k1, 0x594e3a, 11, x);
			Raster.method339(y + 11 + l1 + k1, 0x5d513c, 9, x);
			Raster.method339(y + 11 + l1 + k1, 0x61553e, 7, x);
			Raster.method339(y + 11 + l1 + k1, 0x655841, 5, x);
			Raster.method339(y + 11 + l1 + k1, 0x6a5c43, 4, x);
			Raster.method339(y + 11 + l1 + k1, 0x73654a, 3, x);
			Raster.method339(y + 11 + l1 + k1, 0x7b6a4d, 2, x);
			Raster.method339(y + 11 + l1 + k1, 0x000001, 1, x);
		}
	}

	private void updateNPCs(Buffer stream, int i) {
		anInt839 = 0;
		anInt893 = 0;
		method139(stream);
		updateNPCMovement(i, stream);
		npcUpdateMask(stream);
		for (int k = 0; k < anInt839; k++) {
			int l = anIntArray840[k];
			if (npcs[l].anInt1537 != loopCycle) {
				npcs[l].desc = null;
				npcs[l] = null;
			}
		}

		if (stream.currentPosition != i) {
			Signlink.reporterror(myUsername
					+ " size mismatch in getnpcpos - pos:"
					+ stream.currentPosition + " psize:" + i);
			throw new RuntimeException("eek");
		}
		for (int i1 = 0; i1 < npcCount; i1++)
			if (npcs[npcIndices[i1]] == null) {
				Signlink.reporterror(myUsername
						+ " null entry in npc list - pos:" + i1 + " size:"
						+ npcCount);
				throw new RuntimeException("eek");
			}

	}

	private int cButtonHPos;
	private int cButtonCPos;
	private int setChannel;

	public void processChatModeClick() {
		final int yOffset = frameMode == ScreenMode.FIXED ? 0
				: frameHeight - 503;
		if (super.mouseX >= 5 && super.mouseX <= 61
				&& super.mouseY >= yOffset + 482
				&& super.mouseY <= yOffset + 503) {
			cButtonHPos = 0;
			inputTaken = true;
		} else if (super.mouseX >= 71 && super.mouseX <= 127
				&& super.mouseY >= yOffset + 482
				&& super.mouseY <= yOffset + 503) {
			cButtonHPos = 1;
			inputTaken = true;
		} else if (super.mouseX >= 137 && super.mouseX <= 193
				&& super.mouseY >= yOffset + 482
				&& super.mouseY <= yOffset + 503) {
			cButtonHPos = 2;
			inputTaken = true;
		} else if (super.mouseX >= 203 && super.mouseX <= 259
				&& super.mouseY >= yOffset + 482
				&& super.mouseY <= yOffset + 503) {
			cButtonHPos = 3;
			inputTaken = true;
		} else if (super.mouseX >= 269 && super.mouseX <= 325
				&& super.mouseY >= yOffset + 482
				&& super.mouseY <= yOffset + 503) {
			cButtonHPos = 4;
			inputTaken = true;
		} else if (super.mouseX >= 335 && super.mouseX <= 391
				&& super.mouseY >= yOffset + 482
				&& super.mouseY <= yOffset + 503) {
			cButtonHPos = 5;
			inputTaken = true;
		} else if (super.mouseX >= 404 && super.mouseX <= 515
				&& super.mouseY >= yOffset + 482
				&& super.mouseY <= yOffset + 503) {
			cButtonHPos = 6;
			inputTaken = true;
		} else {
			cButtonHPos = -1;
			inputTaken = true;
		}
		if (super.clickMode3 == 1) {
			if (super.saveClickX >= 5 && super.saveClickX <= 61
					&& super.saveClickY >= yOffset + 482
					&& super.saveClickY <= yOffset + 505) {
				if (frameMode != ScreenMode.FIXED) {
					if (setChannel != 0) {
						cButtonCPos = 0;
						chatTypeView = 0;
						inputTaken = true;
						setChannel = 0;
					} else {
						showChatComponents = showChatComponents ? false : true;
					}
				} else {
					cButtonCPos = 0;
					chatTypeView = 0;
					inputTaken = true;
					setChannel = 0;
				}
			} else if (super.saveClickX >= 71 && super.saveClickX <= 127
					&& super.saveClickY >= yOffset + 482
					&& super.saveClickY <= yOffset + 505) {
				if (frameMode != ScreenMode.FIXED) {
					if (setChannel != 1 && frameMode != ScreenMode.FIXED) {
						cButtonCPos = 1;
						chatTypeView = 5;
						inputTaken = true;
						setChannel = 1;
					} else {
						showChatComponents = showChatComponents ? false : true;
					}
				} else {
					cButtonCPos = 1;
					chatTypeView = 5;
					inputTaken = true;
					setChannel = 1;
				}
			} else if (super.saveClickX >= 137 && super.saveClickX <= 193
					&& super.saveClickY >= yOffset + 482
					&& super.saveClickY <= yOffset + 505) {
				if (frameMode != ScreenMode.FIXED) {
					if (setChannel != 2 && frameMode != ScreenMode.FIXED) {
						cButtonCPos = 2;
						chatTypeView = 1;
						inputTaken = true;
						setChannel = 2;
					} else {
						showChatComponents = showChatComponents ? false : true;
					}
				} else {
					cButtonCPos = 2;
					chatTypeView = 1;
					inputTaken = true;
					setChannel = 2;
				}
			} else if (super.saveClickX >= 203 && super.saveClickX <= 259
					&& super.saveClickY >= yOffset + 482
					&& super.saveClickY <= yOffset + 505) {
				if (frameMode != ScreenMode.FIXED) {
					if (setChannel != 3 && frameMode != ScreenMode.FIXED) {
						cButtonCPos = 3;
						chatTypeView = 2;
						inputTaken = true;
						setChannel = 3;
					} else {
						showChatComponents = showChatComponents ? false : true;
					}
				} else {
					cButtonCPos = 3;
					chatTypeView = 2;
					inputTaken = true;
					setChannel = 3;
				}
			} else if (super.saveClickX >= 269 && super.saveClickX <= 325
					&& super.saveClickY >= yOffset + 482
					&& super.saveClickY <= yOffset + 505) {
				if (frameMode != ScreenMode.FIXED) {
					if (setChannel != 4 && frameMode != ScreenMode.FIXED) {
						cButtonCPos = 4;
						chatTypeView = 11;
						inputTaken = true;
						setChannel = 4;
					} else {
						showChatComponents = showChatComponents ? false : true;
					}
				} else {
					cButtonCPos = 4;
					chatTypeView = 11;
					inputTaken = true;
					setChannel = 4;
				}
			} else if (super.saveClickX >= 335 && super.saveClickX <= 391
					&& super.saveClickY >= yOffset + 482
					&& super.saveClickY <= yOffset + 505) {
				if (frameMode != ScreenMode.FIXED) {
					if (setChannel != 5 && frameMode != ScreenMode.FIXED) {
						cButtonCPos = 5;
						chatTypeView = 3;
						inputTaken = true;
						setChannel = 5;
					} else {
						showChatComponents = showChatComponents ? false : true;
					}
				} else {
					cButtonCPos = 5;
					chatTypeView = 3;
					inputTaken = true;
					setChannel = 5;
				}
			} else if (super.saveClickX >= 404 && super.saveClickX <= 515
					&& super.saveClickY >= yOffset + 482
					&& super.saveClickY <= yOffset + 505) {
				if (openInterfaceId == -1) {
					clearTopInterfaces();
					reportAbuseInput = "";
					canMute = false;
					for (int i = 0; i < Widget.interfaceCache.length; i++) {
						if (Widget.interfaceCache[i] == null
								|| Widget.interfaceCache[i].contentType != 600) {
							continue;
						}
						reportAbuseInterfaceID = openInterfaceId = Widget.interfaceCache[i].parent;
						break;
					}
				} else {
					pushMessage(
							"Please close the interface you have open before using 'report abuse'",
							0, "");
				}
			}
		}
	}

	private void adjustVolume(int i) {
		int j = VariableParameter.parameters[i].anInt709;
		if (j == 0)
			return;
		int k = variousSettings[i];
		if (j == 1) {
			if (k == 1)
				Rasterizer.method372(0.90000000000000002D);
			if (k == 2)
				Rasterizer.method372(0.80000000000000004D);
			if (k == 3)
				Rasterizer.method372(0.69999999999999996D);
			if (k == 4)
				Rasterizer.method372(0.59999999999999998D);
			ItemDefinition.image_cache.unlinkAll();
			welcomeScreenRaised = true;
		}

		if (j == 3) {
			boolean flag1 = Configuration.enableMusic;
			if (k == 0) {
				if (Signlink.music != null)
					adjustVolume(Configuration.enableMusic, 500);
				Configuration.enableMusic = true;
			}
			if (k == 1) {
				if (Signlink.music != null)
					adjustVolume(Configuration.enableMusic, 300);
				Configuration.enableMusic = true;
			}
			if (k == 2) {
				if (Signlink.music != null)
					adjustVolume(Configuration.enableMusic, 100);
				Configuration.enableMusic = true;
			}
			if (k == 3) {
				if (Signlink.music != null)
					adjustVolume(Configuration.enableMusic, 0);
				Configuration.enableMusic = true;
			}
			if (k == 4)
				Configuration.enableMusic = false;
			if (Configuration.enableMusic != flag1 && !lowMem) {
				if (Configuration.enableMusic) {
					nextSong = currentSong;
					songChanging = true;
					onDemandFetcher.provide(2, nextSong);
				} else {
					stopMidi();
				}
				prevSong = 0;
			}
		}

		if (j == 4) {
			SoundPlayer.setVolume(k);
			if (k == 0) {
				aBoolean848 = true;
				setWaveVolume(0);
			}
			if (k == 1) {
				aBoolean848 = true;
				setWaveVolume(-400);
			}
			if (k == 2) {
				aBoolean848 = true;
				setWaveVolume(-800);
			}
			if (k == 3) {
				aBoolean848 = true;
				setWaveVolume(-1200);
			}
			if (k == 4)
				aBoolean848 = false;
		}

		if (j == 5)
			anInt1253 = k;
		if (j == 6)
			anInt1249 = k;
		if (j == 8) {
			splitPrivateChat = k;
			inputTaken = true;
		}
		if (j == 9)
			anInt913 = k;
	}

	public CacheArchive mediaStreamLoader;

	private final int[] hitmarks562 = { 31, 32, 33, 34 };

	public void updateEntities() {
		try {
			int messageLength = 0;
			for (int j = -1; j < playerCount + npcCount; j++) {
				Object obj;
				if (j == -1)
					obj = localPlayer;
				else if (j < playerCount)
					obj = players[playerIndices[j]];
				else
					obj = npcs[npcIndices[j - playerCount]];
				if (obj == null || !((Entity) (obj)).isVisible())
					continue;
				if (obj instanceof Npc) {
					NpcDefinition entityDef = ((Npc) obj).desc;
					if (Configuration.namesAboveHeads) {
						npcScreenPos(((Entity) (obj)),
								((Entity) (obj)).height + 15);
						smallText.drawText(0x0099FF, entityDef.name,
								spriteDrawY - 5, spriteDrawX); // -15
																// from
																// original
					}
					if (entityDef.childrenIDs != null)
						entityDef = entityDef.morph();
					if (entityDef == null)
						continue;
				}
				if (j < playerCount) {
					int l = 30;
					Player player = (Player) obj;
					if (player.headIcon >= 0) {
						npcScreenPos(((Entity) (obj)),
								((Entity) (obj)).height + 15);
						if (spriteDrawX > -1) {
							if (player.skullIcon < 2) {
								skullIcons[player.skullIcon].drawSprite(
										spriteDrawX - 12, spriteDrawY - l);
								l += 25;
							}
							if (player.headIcon < 13) {
								headIcons[player.headIcon].drawSprite(
										spriteDrawX - 12, spriteDrawY - l);
								l += 18;
							}
						}
					}
					if (j >= 0 && hintIconDrawType == 10
							&& hintIconPlayerId == playerIndices[j]) {
						npcScreenPos(((Entity) (obj)),
								((Entity) (obj)).height + 15);
						if (spriteDrawX > -1)
							headIconsHint[player.hintIcon].drawSprite(
									spriteDrawX - 12, spriteDrawY - l);
					}
					if (Configuration.hpAboveHeads
							&& Configuration.namesAboveHeads) {
						newSmallFont
								.drawCenteredString(
										(new StringBuilder())
												.append(((Entity) (Entity) obj).currentHealth)
												.append("/")
												.append(((Entity) (Entity) obj).maxHealth)
												.toString(), spriteDrawX,
										spriteDrawY - 29, 0x3399ff, 100);
					} // draws HP above head
					else if (Configuration.hpAboveHeads
							&& !Configuration.namesAboveHeads) {
						newSmallFont
								.drawCenteredString(
										(new StringBuilder())
												.append(((Entity) (Entity) obj).currentHealth)
												.append("/")
												.append(((Entity) (Entity) obj).maxHealth)
												.toString(), spriteDrawX,
										spriteDrawY - 5, 0x3399ff, 100);
					}
					if (Configuration.namesAboveHeads) {
						npcScreenPos(((Entity) (obj)),
								((Entity) (obj)).height + 15);
						int col = 0x0000ff;
						if (player.clanName == localPlayer.clanName)
							col = 0x00ff00;
						smallText.drawText(col, player.name, spriteDrawY - 15,
								spriteDrawX);
						if (player.clanName != "")
							smallText.drawText(col,
									"<" + player.clanName + ">",
									spriteDrawY - 5, spriteDrawX);
					}
				} else {
					NpcDefinition entityDef_1 = ((Npc) obj).desc;
					if (entityDef_1.headIcon >= 0
							&& entityDef_1.headIcon < headIcons.length) {
						npcScreenPos(((Entity) (obj)),
								((Entity) (obj)).height + 15);
						if (spriteDrawX > -1)
							headIcons[entityDef_1.headIcon].drawSprite(
									spriteDrawX - 12, spriteDrawY - 30);
					}
					if (hintIconDrawType == 1
							&& hintIconNpcId == npcIndices[j - playerCount]
							&& loopCycle % 20 < 10) {
						npcScreenPos(((Entity) (obj)),
								((Entity) (obj)).height + 15);
						if (spriteDrawX > -1)
							headIconsHint[0].drawSprite(spriteDrawX - 12,
									spriteDrawY - 28);
					}
				}
				if (((Entity) (obj)).spokenText != null
						&& (j >= playerCount || publicChatMode == 0
								|| publicChatMode == 3 || publicChatMode == 1
								&& isFriendOrSelf(((Player) obj).name))) {
					npcScreenPos(((Entity) (obj)), ((Entity) (obj)).height);
					if (spriteDrawX > -1 && messageLength < anInt975) {
						anIntArray979[messageLength] = boldText
								.method384(((Entity) (obj)).spokenText) / 2;
						anIntArray978[messageLength] = boldText.anInt1497;
						anIntArray976[messageLength] = spriteDrawX;
						anIntArray977[messageLength] = spriteDrawY;
						textColourEffect[messageLength] = ((Entity) (obj)).textColour;
						anIntArray981[messageLength] = ((Entity) (obj)).textEffect;
						anIntArray982[messageLength] = ((Entity) (obj)).textCycle;
						aStringArray983[messageLength++] = ((Entity) (obj)).spokenText;
						if (anInt1249 == 0 && ((Entity) (obj)).textEffect >= 1
								&& ((Entity) (obj)).textEffect <= 3) {
							anIntArray978[messageLength] += 10;
							anIntArray977[messageLength] += 5;
						}
						if (anInt1249 == 0 && ((Entity) (obj)).textEffect == 4)
							anIntArray979[messageLength] = 60;
						if (anInt1249 == 0 && ((Entity) (obj)).textEffect == 5)
							anIntArray978[messageLength] += 5;
					}
				}
				if (((Entity) (obj)).loopCycleStatus > loopCycle) {
					try {
						npcScreenPos(((Entity) (obj)),
								((Entity) (obj)).height + 15);
						if (spriteDrawX > -1) {
							int i1 = (((Entity) (obj)).currentHealth * 30)
									/ ((Entity) (obj)).maxHealth;

							if (i1 > 30) {
								i1 = 30;
							}
							int hpPercent = (((Entity) (obj)).currentHealth * 56)
									/ ((Entity) (obj)).maxHealth;

							if (hpPercent > 56) {
								hpPercent = 56;
							}
							if (!Configuration.hpBar554) {
								Raster.drawPixels(5, spriteDrawY - 3,
										spriteDrawX - 15, 65280, i1);
								Raster.drawPixels(5, spriteDrawY - 3,
										(spriteDrawX - 15) + i1, 0xff0000,
										30 - i1);
							} else {
								cacheSprite[41].drawSprite(spriteDrawX - 28,
										spriteDrawY - 3);
								cacheSprite[40] = new Sprite(
										Signlink.findcachedir()
												+ "Sprites/Attack/40.png",
										hpPercent, 7);
								cacheSprite[40].drawSprite(spriteDrawX - 28,
										spriteDrawY - 3);
							}
						}
					} catch (Exception e) {
					}
				}
				if (!Configuration.hitmarks554) {
					for (int j1 = 0; j1 < 4; j1++) {
						if (((Entity) (obj)).hitsLoopCycle[j1] > loopCycle) {
							npcScreenPos(((Entity) (obj)),
									((Entity) (obj)).height / 2);
							if (spriteDrawX > -1) {
								if (j1 == 1)
									spriteDrawY -= 20;
								if (j1 == 2) {
									spriteDrawX -= 15;
									spriteDrawY -= 10;
								}
								if (j1 == 3) {
									spriteDrawX += 15;
									spriteDrawY -= 10;
								}
								hitMarks[((Entity) (obj)).hitMarkTypes[j1]]
										.drawSprite(spriteDrawX - 12,
												spriteDrawY - 12);
								if (Configuration.tenXHp) {
									smallText
											.drawText(
													0,
													String.valueOf(((Entity) (obj)).hitDamages[j1] * 10),
													spriteDrawY + 4,
													spriteDrawX);
								} else {
									smallText
											.drawText(
													0,
													String.valueOf(((Entity) (obj)).hitDamages[j1]),
													spriteDrawY + 4,
													spriteDrawX);
								}

								if (Configuration.tenXHp) {
									smallText
											.drawText(
													0xffffff,
													String.valueOf(((Entity) (obj)).hitDamages[j1] * 10),
													spriteDrawY + 3,
													spriteDrawX - 1);
								} else {
									smallText
											.drawText(
													0xffffff,
													String.valueOf(((Entity) (obj)).hitDamages[j1]),
													spriteDrawY + 3,
													spriteDrawX - 1);
								}
							}
						}
					}
				} else {
					for (int j2 = 0; j2 < 4; j2++) {
						if (((Entity) (obj)).hitsLoopCycle[j2] > loopCycle) {
							npcScreenPos(((Entity) (obj)),
									((Entity) (obj)).height / 2);
							if (spriteDrawX > -1) {
								if (j2 == 0
										&& ((Entity) (obj)).hitDamages[j2] > 99)
									((Entity) (obj)).hitMarkTypes[j2] = 3;
								else if (j2 == 1
										&& ((Entity) (obj)).hitDamages[j2] > 99)
									((Entity) (obj)).hitMarkTypes[j2] = 3;
								else if (j2 == 2
										&& ((Entity) (obj)).hitDamages[j2] > 99)
									((Entity) (obj)).hitMarkTypes[j2] = 3;
								else if (j2 == 3
										&& ((Entity) (obj)).hitDamages[j2] > 99)
									((Entity) (obj)).hitMarkTypes[j2] = 3;
								if (j2 == 1) {
									spriteDrawY -= 20;
								}
								if (j2 == 2) {
									spriteDrawX -= (((Entity) (obj)).hitDamages[j2] > 99 ? 30
											: 20);
									spriteDrawY -= 10;
								}
								if (j2 == 3) {
									spriteDrawX += (((Entity) (obj)).hitDamages[j2] > 99 ? 30
											: 20);
									spriteDrawY -= 10;
								}
								if (((Entity) (obj)).hitMarkTypes[j2] == 3) {
									spriteDrawX -= 8;
								}
								cacheSprite[hitmarks562[((Entity) (obj)).hitMarkTypes[j2]]]
										.draw24BitSprite(spriteDrawX - 12,
												spriteDrawY - 12);
								smallText
										.drawText(
												0xffffff,
												String.valueOf(((Entity) (obj)).hitDamages[j2]),
												spriteDrawY + 3,
												(((Entity) (obj)).hitMarkTypes[j2] == 3 ? spriteDrawX + 7
														: spriteDrawX - 1));
							}
						}
					}
				}
			}
			for (int defaultText = 0; defaultText < messageLength; defaultText++) {
				int k1 = anIntArray976[defaultText];
				int l1 = anIntArray977[defaultText];
				int j2 = anIntArray979[defaultText];
				int k2 = anIntArray978[defaultText];
				boolean flag = true;
				while (flag) {
					flag = false;
					for (int l2 = 0; l2 < defaultText; l2++)
						if (l1 + 2 > anIntArray977[l2] - anIntArray978[l2]
								&& l1 - k2 < anIntArray977[l2] + 2
								&& k1 - j2 < anIntArray976[l2]
										+ anIntArray979[l2]
								&& k1 + j2 > anIntArray976[l2]
										- anIntArray979[l2]
								&& anIntArray977[l2] - anIntArray978[l2] < l1) {
							l1 = anIntArray977[l2] - anIntArray978[l2];
							flag = true;
						}

				}
				spriteDrawX = anIntArray976[defaultText];
				spriteDrawY = anIntArray977[defaultText] = l1;
				String s = aStringArray983[defaultText];
				if (anInt1249 == 0) {
					int i3 = 0xffff00;
					if (textColourEffect[defaultText] < 6)
						i3 = anIntArray965[textColourEffect[defaultText]];
					if (textColourEffect[defaultText] == 6)
						i3 = anInt1265 % 20 >= 10 ? 0xffff00 : 0xff0000;
					if (textColourEffect[defaultText] == 7)
						i3 = anInt1265 % 20 >= 10 ? 65535 : 255;
					if (textColourEffect[defaultText] == 8)
						i3 = anInt1265 % 20 >= 10 ? 0x80ff80 : 45056;
					if (textColourEffect[defaultText] == 9) {
						int j3 = 150 - anIntArray982[defaultText];
						if (j3 < 50)
							i3 = 0xff0000 + 1280 * j3;
						else if (j3 < 100)
							i3 = 0xffff00 - 0x50000 * (j3 - 50);
						else if (j3 < 150)
							i3 = 65280 + 5 * (j3 - 100);
					}
					if (textColourEffect[defaultText] == 10) {
						int k3 = 150 - anIntArray982[defaultText];
						if (k3 < 50)
							i3 = 0xff0000 + 5 * k3;
						else if (k3 < 100)
							i3 = 0xff00ff - 0x50000 * (k3 - 50);
						else if (k3 < 150)
							i3 = (255 + 0x50000 * (k3 - 100)) - 5 * (k3 - 100);
					}
					if (textColourEffect[defaultText] == 11) {
						int l3 = 150 - anIntArray982[defaultText];
						if (l3 < 50)
							i3 = 0xffffff - 0x50005 * l3;
						else if (l3 < 100)
							i3 = 65280 + 0x50005 * (l3 - 50);
						else if (l3 < 150)
							i3 = 0xffffff - 0x50000 * (l3 - 100);
					}
					if (anIntArray981[defaultText] == 0) {
						boldText.drawText(0, s, spriteDrawY + 1, spriteDrawX);
						boldText.drawText(i3, s, spriteDrawY, spriteDrawX);
					}
					if (anIntArray981[defaultText] == 1) {
						boldText.wave(0, s, spriteDrawX, anInt1265,
								spriteDrawY + 1);
						boldText.wave(i3, s, spriteDrawX, anInt1265,
								spriteDrawY);
					}
					if (anIntArray981[defaultText] == 2) {
						boldText.wave2(spriteDrawX, s, anInt1265,
								spriteDrawY + 1, 0);
						boldText.wave2(spriteDrawX, s, anInt1265, spriteDrawY,
								i3);
					}
					if (anIntArray981[defaultText] == 3) {
						boldText.shake(150 - anIntArray982[defaultText], s,
								anInt1265, spriteDrawY + 1, spriteDrawX, 0);
						boldText.shake(150 - anIntArray982[defaultText], s,
								anInt1265, spriteDrawY, spriteDrawX, i3);
					}
					if (anIntArray981[defaultText] == 4) {
						int i4 = boldText.method384(s);
						int k4 = ((150 - anIntArray982[defaultText]) * (i4 + 100)) / 150;
						Raster.setDrawingArea(334, spriteDrawX - 50,
								spriteDrawX + 50, 0);
						boldText.method385(0, s, spriteDrawY + 1,
								(spriteDrawX + 50) - k4);
						boldText.method385(i3, s, spriteDrawY,
								(spriteDrawX + 50) - k4);
						Raster.defaultDrawingAreaSize();
					}
					if (anIntArray981[defaultText] == 5) {
						int j4 = 150 - anIntArray982[defaultText];
						int l4 = 0;
						if (j4 < 25)
							l4 = j4 - 25;
						else if (j4 > 125)
							l4 = j4 - 125;
						Raster.setDrawingArea(spriteDrawY + 5, 0, 512,
								spriteDrawY - boldText.anInt1497 - 1);
						boldText.drawText(0, s, spriteDrawY + 1 + l4,
								spriteDrawX);
						boldText.drawText(i3, s, spriteDrawY + l4, spriteDrawX);
						Raster.defaultDrawingAreaSize();
					}
				} else {
					boldText.drawText(0, s, spriteDrawY + 1, spriteDrawX);
					boldText.drawText(0xffff00, s, spriteDrawY, spriteDrawX);
				}
			}
		} catch (Exception e) {
		}
	}

	private void delFriend(long l) {
		try {
			if (l == 0L)
				return;
			for (int i = 0; i < friendsCount; i++) {
				if (friendsListAsLongs[i] != l)
					continue;
				friendsCount--;
				for (int j = i; j < friendsCount; j++) {
					friendsList[j] = friendsList[j + 1];
					friendsNodeIDs[j] = friendsNodeIDs[j + 1];
					friendsListAsLongs[j] = friendsListAsLongs[j + 1];
				}

				outgoing.createFrame(215);
				outgoing.writeLong(l);
				break;
			}
		} catch (RuntimeException runtimeexception) {
			Signlink.reporterror("18622, " + false + ", " + l + ", "
					+ runtimeexception.toString());
			throw new RuntimeException();
		}
	}

	private final int[] sideIconsX = { 17, 49, 83, 114, 146, 180, 214, 16, 49,
			82, 116, 148, 184, 216 }, sideIconsY = { 9, 7, 7, 5, 2, 3, 7, 303,
			306, 306, 302, 305, 303, 303, 303 }, sideIconsId = { 0, 1, 2, 3, 4,
			5, 6, 7, 8, 9, 10, 11, 12, 13 }, sideIconsTab = { 0, 1, 2, 3, 4, 5,
			6, 7, 8, 9, 10, 11, 12, 13 };

	public void drawSideIcons() {
		int xOffset = frameMode == ScreenMode.FIXED ? 0 : frameWidth - 247;
		int yOffset = frameMode == ScreenMode.FIXED ? 0 : frameHeight - 336;
		if (frameMode == ScreenMode.FIXED || frameMode != ScreenMode.FIXED
				&& !changeTabArea) {
			for (int i = 0; i < sideIconsTab.length; i++) {
				if (tabInterfaceIDs[sideIconsTab[i]] != -1) {
					if (sideIconsId[i] != -1) {
						sideIcons[sideIconsId[i]].drawSprite(sideIconsX[i]
								+ xOffset, sideIconsY[i] + yOffset);
					}
				}
			}
		} else if (changeTabArea && frameWidth < 1000) {
			int[] iconId = { 0, 1, 2, 3, 4, 5, 6, -1, 8, 9, 7, 11, 12, 13 };
			int[] iconX = { 219, 189, 156, 126, 93, 62, 30, 219, 189, 156, 124,
					92, 59, 28 };
			int[] iconY = { 67, 69, 67, 69, 72, 72, 69, 32, 29, 29, 32, 30, 33,
					31, 32 };
			for (int i = 0; i < sideIconsTab.length; i++) {
				if (tabInterfaceIDs[sideIconsTab[i]] != -1) {
					if (iconId[i] != -1) {
						sideIcons[iconId[i]].drawSprite(frameWidth - iconX[i],
								frameHeight - iconY[i]);
					}
				}
			}
		} else if (changeTabArea && frameWidth >= 1000) {
			int[] iconId = { 0, 1, 2, 3, 4, 5, 6, -1, 8, 9, 7, 11, 12, 13 };
			int[] iconX = { 50, 80, 114, 143, 176, 208, 240, 242, 273, 306,
					338, 370, 404, 433 };
			int[] iconY = { 30, 32, 30, 32, 34, 34, 32, 32, 29, 29, 32, 31, 32,
					32, 32 };
			for (int i = 0; i < sideIconsTab.length; i++) {
				if (tabInterfaceIDs[sideIconsTab[i]] != -1) {
					if (iconId[i] != -1) {
						sideIcons[iconId[i]].drawSprite(frameWidth - 461
								+ iconX[i], frameHeight - iconY[i]);
					}
				}
			}
		}
	}

	private final int[] redStonesX = { 6, 44, 77, 110, 143, 176, 209, 6, 44,
			77, 110, 143, 176, 209 }, redStonesY = { 0, 0, 0, 0, 0, 0, 0, 298,
			298, 298, 298, 298, 298, 298 }, redStonesId = { 35, 39, 39, 39, 39,
			39, 36, 37, 39, 39, 39, 39, 39, 38 };

	private void drawRedStones() {
		int xOffset = frameMode == ScreenMode.FIXED ? 0 : frameWidth - 247;
		int yOffset = frameMode == ScreenMode.FIXED ? 0 : frameHeight - 336;
		if (frameMode == ScreenMode.FIXED || frameMode != ScreenMode.FIXED
				&& !changeTabArea) {
			if (tabInterfaceIDs[tabID] != -1 && tabID != 15) {
				cacheSprite[redStonesId[tabID]].drawSprite(redStonesX[tabID]
						+ xOffset, redStonesY[tabID] + yOffset);
			}
		} else if (changeTabArea && frameWidth < 1000) {
			int[] stoneX = { 226, 194, 162, 130, 99, 65, 34, 219, 195, 161,
					130, 98, 65, 33 };
			int[] stoneY = { 73, 73, 73, 73, 73, 73, 73, -1, 37, 37, 37, 37,
					37, 37, 37 };
			if (tabInterfaceIDs[tabID] != -1 && tabID != 10
					&& showTabComponents) {
				if (tabID == 7) {
					cacheSprite[39].drawSprite(frameWidth - 130,
							frameHeight - 37);
				}
				cacheSprite[39].drawSprite(frameWidth - stoneX[tabID],
						frameHeight - stoneY[tabID]);
			}
		} else if (changeTabArea && frameWidth >= 1000) {
			int[] stoneX = { 417, 385, 353, 321, 289, 256, 224, 129, 193, 161,
					130, 98, 65, 33 };
			if (tabInterfaceIDs[tabID] != -1 && tabID != 10
					&& showTabComponents) {
				cacheSprite[39].drawSprite(frameWidth - stoneX[tabID],
						frameHeight - 37);
			}
		}
	}

	private void drawTabArea() {
		final int xOffset = frameMode == ScreenMode.FIXED ? 0
				: frameWidth - 241;
		final int yOffset = frameMode == ScreenMode.FIXED ? 0
				: frameHeight - 336;
		if (frameMode == ScreenMode.FIXED) {
			tabImageProducer.initDrawingArea();
		}
		Rasterizer.anIntArray1472 = anIntArray1181;
		if (frameMode == ScreenMode.FIXED) {
			cacheSprite[21].drawSprite(0, 0);
		} else if (frameMode != ScreenMode.FIXED && !changeTabArea) {
			Raster.method335(0x3E3529, frameHeight - 304, 195, 270,
					transparentTabArea ? 80 : 256, frameWidth - 217);
			cacheSprite[47].drawSprite(xOffset, yOffset);
		} else {
			if (frameWidth >= 1000) {
				if (showTabComponents) {
					Raster.method335(0x3E3529, frameHeight - 304, 197, 265,
							transparentTabArea ? 80 : 256, frameWidth - 197);
					cacheSprite[50].drawSprite(frameWidth - 204,
							frameHeight - 311);
				}
				for (int x = frameWidth - 417, y = frameHeight - 37, index = 0; x <= frameWidth - 30
						&& index < 13; x += 32, index++) {
					cacheSprite[46].drawSprite(x, y);
				}
			} else if (frameWidth < 1000) {
				if (showTabComponents) {
					Raster.method335(0x3E3529, frameHeight - 341, 195, 265,
							transparentTabArea ? 80 : 256, frameWidth - 197);
					cacheSprite[50].drawSprite(frameWidth - 204,
							frameHeight - 348);
				}
				for (int x = frameWidth - 226, y = frameHeight - 73, index = 0; x <= frameWidth - 32
						&& index < 7; x += 32, index++) {
					cacheSprite[46].drawSprite(x, y);
				}
				for (int x = frameWidth - 226, y = frameHeight - 37, index = 0; x <= frameWidth - 32
						&& index < 7; x += 32, index++) {
					cacheSprite[46].drawSprite(x, y);
				}
			}
		}
		if (overlayInterfaceId == -1) {
			drawRedStones();
			drawSideIcons();
		}
		if (showTabComponents) {
			int x = frameMode == ScreenMode.FIXED ? 31 : frameWidth - 215;
			int y = frameMode == ScreenMode.FIXED ? 37 : frameHeight - 299;
			if (changeTabArea) {
				x = frameWidth - 197;
				y = frameWidth >= 1000 ? frameHeight - 303 : frameHeight - 340;
			}
			if (overlayInterfaceId != -1) {
				drawInterface(0, x, Widget.interfaceCache[overlayInterfaceId],
						y);
			} else if (tabInterfaceIDs[tabID] != -1) {
				drawInterface(0, x,
						Widget.interfaceCache[tabInterfaceIDs[tabID]], y);
			}
		}
		if (menuOpen) {
			drawMenu(frameMode == ScreenMode.FIXED ? 516 : 0,
					frameMode == ScreenMode.FIXED ? 168 : 0);
		}
		if (frameMode == ScreenMode.FIXED) {
			tabImageProducer.drawGraphics(168, super.graphics, 516);
			gameScreenImageProducer.initDrawingArea();
		}
		Rasterizer.anIntArray1472 = anIntArray1182;
	}

	private void writeBackgroundTexture(int j) {
		if (!lowMem) {
			if (Rasterizer.anIntArray1480[17] >= j) {
				Background background = Rasterizer.aBackgroundArray1474s[17];
				int k = background.anInt1452 * background.anInt1453 - 1;
				int j1 = background.anInt1452 * anInt945 * 2;
				byte abyte0[] = background.aByteArray1450;
				byte abyte3[] = aByteArray912;
				for (int i2 = 0; i2 <= k; i2++)
					abyte3[i2] = abyte0[i2 - j1 & k];

				background.aByteArray1450 = abyte3;
				aByteArray912 = abyte0;
				Rasterizer.method370(17);
				anInt854++;
				if (anInt854 > 1235) {
					anInt854 = 0;
					outgoing.createFrame(226);
					outgoing.writeByte(0);
					int l2 = outgoing.currentPosition;
					outgoing.writeShort(58722);
					outgoing.writeByte(240);
					outgoing.writeShort((int) (Math.random() * 65536D));
					outgoing.writeByte((int) (Math.random() * 256D));
					if ((int) (Math.random() * 2D) == 0)
						outgoing.writeShort(51825);
					outgoing.writeByte((int) (Math.random() * 256D));
					outgoing.writeShort((int) (Math.random() * 65536D));
					outgoing.writeShort(7130);
					outgoing.writeShort((int) (Math.random() * 65536D));
					outgoing.writeShort(61657);
					outgoing.writeBytes(outgoing.currentPosition - l2);
				}
			}
			if (Rasterizer.anIntArray1480[24] >= j) {
				Background background_1 = Rasterizer.aBackgroundArray1474s[24];
				int l = background_1.anInt1452 * background_1.anInt1453 - 1;
				int k1 = background_1.anInt1452 * anInt945 * 2;
				byte abyte1[] = background_1.aByteArray1450;
				byte abyte4[] = aByteArray912;
				for (int j2 = 0; j2 <= l; j2++)
					abyte4[j2] = abyte1[j2 - k1 & l];

				background_1.aByteArray1450 = abyte4;
				aByteArray912 = abyte1;
				Rasterizer.method370(24);
			}
			if (Rasterizer.anIntArray1480[34] >= j) {
				Background background_2 = Rasterizer.aBackgroundArray1474s[34];
				int i1 = background_2.anInt1452 * background_2.anInt1453 - 1;
				int l1 = background_2.anInt1452 * anInt945 * 2;
				byte abyte2[] = background_2.aByteArray1450;
				byte abyte5[] = aByteArray912;
				for (int k2 = 0; k2 <= i1; k2++)
					abyte5[k2] = abyte2[k2 - l1 & i1];

				background_2.aByteArray1450 = abyte5;
				aByteArray912 = abyte2;
				Rasterizer.method370(34);
			}
			if (Rasterizer.anIntArray1480[40] >= j) {
				Background background_2 = Rasterizer.aBackgroundArray1474s[40];
				int i1 = background_2.anInt1452 * background_2.anInt1453 - 1;
				int l1 = background_2.anInt1452 * anInt945 * 2;
				byte abyte2[] = background_2.aByteArray1450;
				byte abyte5[] = aByteArray912;
				for (int k2 = 0; k2 <= i1; k2++)
					abyte5[k2] = abyte2[k2 - l1 & i1];

				background_2.aByteArray1450 = abyte5;
				aByteArray912 = abyte2;
				Rasterizer.method370(40);
			}
		}
	}

	private void resetSpokenText() {
		for (int i = -1; i < playerCount; i++) {
			int j;
			if (i == -1)
				j = internalLocalPlayerIndex;
			else
				j = playerIndices[i];
			Player player = players[j];
			if (player != null && player.textCycle > 0) {
				player.textCycle--;
				if (player.textCycle == 0)
					player.spokenText = null;
			}
		}
		for (int k = 0; k < npcCount; k++) {
			int l = npcIndices[k];
			Npc npc = npcs[l];
			if (npc != null && npc.textCycle > 0) {
				npc.textCycle--;
				if (npc.textCycle == 0)
					npc.spokenText = null;
			}
		}
	}

	private void calcCameraPos() {
		int i = x * 128 + 64;
		int j = y * 128 + 64;
		int k = method42(plane, j, i) - height;
		if (xCameraPos < i) {
			xCameraPos += speed + ((i - xCameraPos) * angle) / 1000;
			if (xCameraPos > i)
				xCameraPos = i;
		}
		if (xCameraPos > i) {
			xCameraPos -= speed + ((xCameraPos - i) * angle) / 1000;
			if (xCameraPos < i)
				xCameraPos = i;
		}
		if (zCameraPos < k) {
			zCameraPos += speed + ((k - zCameraPos) * angle) / 1000;
			if (zCameraPos > k)
				zCameraPos = k;
		}
		if (zCameraPos > k) {
			zCameraPos -= speed + ((zCameraPos - k) * angle) / 1000;
			if (zCameraPos < k)
				zCameraPos = k;
		}
		if (yCameraPos < j) {
			yCameraPos += speed + ((j - yCameraPos) * angle) / 1000;
			if (yCameraPos > j)
				yCameraPos = j;
		}
		if (yCameraPos > j) {
			yCameraPos -= speed + ((yCameraPos - j) * angle) / 1000;
			if (yCameraPos < j)
				yCameraPos = j;
		}
		i = anInt995 * 128 + 64;
		j = anInt996 * 128 + 64;
		k = method42(plane, j, i) - anInt997;
		int l = i - xCameraPos;
		int i1 = k - zCameraPos;
		int j1 = j - yCameraPos;
		int k1 = (int) Math.sqrt(l * l + j1 * j1);
		int l1 = (int) (Math.atan2(i1, k1) * 325.94900000000001D) & 0x7ff;
		int i2 = (int) (Math.atan2(l, j1) * -325.94900000000001D) & 0x7ff;
		if (l1 < 128)
			l1 = 128;
		if (l1 > 383)
			l1 = 383;
		if (yCameraCurve < l1) {
			yCameraCurve += anInt998 + ((l1 - yCameraCurve) * anInt999) / 1000;
			if (yCameraCurve > l1)
				yCameraCurve = l1;
		}
		if (yCameraCurve > l1) {
			yCameraCurve -= anInt998 + ((yCameraCurve - l1) * anInt999) / 1000;
			if (yCameraCurve < l1)
				yCameraCurve = l1;
		}
		int j2 = i2 - xCameraCurve;
		if (j2 > 1024)
			j2 -= 2048;
		if (j2 < -1024)
			j2 += 2048;
		if (j2 > 0) {
			xCameraCurve += anInt998 + (j2 * anInt999) / 1000;
			xCameraCurve &= 0x7ff;
		}
		if (j2 < 0) {
			xCameraCurve -= anInt998 + (-j2 * anInt999) / 1000;
			xCameraCurve &= 0x7ff;
		}
		int k2 = i2 - xCameraCurve;
		if (k2 > 1024)
			k2 -= 2048;
		if (k2 < -1024)
			k2 += 2048;
		if (k2 < 0 && j2 > 0 || k2 > 0 && j2 < 0)
			xCameraCurve = i2;
	}

	public void drawMenu(int x, int y) {
		int xPos = menuOffsetX - (x - 4);
		int yPos = (-y + 4) + menuOffsetY;
		int w = menuWidth;
		int h = menuHeight + 1;
		inputTaken = true;
		tabAreaAltered = true;
		int menuColor = 0x5d5447;
		Raster.drawPixels(h, yPos, xPos, menuColor, w);
		Raster.drawPixels(16, yPos + 1, xPos + 1, 0, w - 2);
		Raster.fillPixels(xPos + 1, w - 2, h - 19, 0, yPos + 18);
		boldText.method385(menuColor, "Choose Option", yPos + 14, xPos + 3);
		int mouseX = super.mouseX - (x);
		int mouseY = (-y) + super.mouseY;
		for (int i = 0; i < menuActionRow; i++) {
			int textY = yPos + 31 + (menuActionRow - 1 - i) * 15;
			int textColor = 0xffffff;
			if (mouseX > xPos && mouseX < xPos + w && mouseY > textY - 13
					&& mouseY < textY + 3) {
				Raster.drawPixels(15, textY - 11, xPos + 3, 0x6f695d,
						menuWidth - 6);
				textColor = 0xffff00;
			}
			boldText.drawTextWithPotentialShadow(true, xPos + 3, textColor,
					menuActionName[i], textY);
		}
	}

	private void addFriend(long l) {
		try {
			if (l == 0L)
				return;
			if (friendsCount >= 100 && anInt1046 != 1) {
				pushMessage(
						"Your friendlist is full. Max of 100 for free users, and 200 for members",
						0, "");
				return;
			}
			if (friendsCount >= 200) {
				pushMessage(
						"Your friendlist is full. Max of 100 for free users, and 200 for members",
						0, "");
				return;
			}
			String s = TextClass.fixName(TextClass.nameForLong(l));
			for (int i = 0; i < friendsCount; i++)
				if (friendsListAsLongs[i] == l) {
					pushMessage(s + " is already on your friend list", 0, "");
					return;
				}
			for (int j = 0; j < ignoreCount; j++)
				if (ignoreListAsLongs[j] == l) {
					pushMessage("Please remove " + s
							+ " from your ignore list first", 0, "");
					return;
				}

			if (s.equals(localPlayer.name)) {
				return;
			} else {
				friendsList[friendsCount] = s;
				friendsListAsLongs[friendsCount] = l;
				friendsNodeIDs[friendsCount] = 0;
				friendsCount++;
				outgoing.createFrame(188);
				outgoing.writeLong(l);
				return;
			}
		} catch (RuntimeException runtimeexception) {
			Signlink.reporterror("15283, " + (byte) 68 + ", " + l + ", "
					+ runtimeexception.toString());
		}
		throw new RuntimeException();
	}

	private int method42(int i, int j, int k) {
		int l = k >> 7;
		int i1 = j >> 7;
		if (l < 0 || i1 < 0 || l > 103 || i1 > 103)
			return 0;
		int j1 = i;
		if (j1 < 3 && (byteGroundArray[1][l][i1] & 2) == 2)
			j1++;
		int k1 = k & 0x7f;
		int l1 = j & 0x7f;
		int i2 = intGroundArray[j1][l][i1] * (128 - k1)
				+ intGroundArray[j1][l + 1][i1] * k1 >> 7;
		int j2 = intGroundArray[j1][l][i1 + 1] * (128 - k1)
				+ intGroundArray[j1][l + 1][i1 + 1] * k1 >> 7;
		return i2 * (128 - l1) + j2 * l1 >> 7;
	}

	private static String intToKOrMil(int j) {
		if (j < 0x186a0)
			return String.valueOf(j);
		if (j < 0x989680)
			return j / 1000 + "K";
		else
			return j / 0xf4240 + "M";
	}

	private void resetLogout() {
		try {
			if (socketStream != null)
				socketStream.close();
		} catch (Exception _ex) {
		}
		socketStream = null;
		loggedIn = false;
		loginScreenState = 0;
		myUsername = "mod wind";
		myPassword = "test";
		unlinkMRUNodes();
		worldController.initToNull();
		for (int i = 0; i < 4; i++)
			aClass11Array1230[i].initialize();
		Arrays.fill(chatMessages, null);
		System.gc();
		stopMidi();
		currentSong = -1;
		nextSong = -1;
		prevSong = 0;
		frameMode(ScreenMode.FIXED);
	}

	private void changeCharacterGender() {
		aBoolean1031 = true;
		for (int j = 0; j < 7; j++) {
			anIntArray1065[j] = -1;
			for (int k = 0; k < IdentityKit.length; k++) {
				if (IdentityKit.cache[k].aBoolean662
						|| IdentityKit.cache[k].anInt657 != j
								+ (maleCharacter ? 0 : 7))
					continue;
				anIntArray1065[j] = k;
				break;
			}
		}
	}

	private void updateNPCMovement(int i, Buffer stream) {
		while (stream.bitPosition + 21 < i * 8) {
			int k = stream.readBits(14);
			if (k == 16383)
				break;
			if (npcs[k] == null)
				npcs[k] = new Npc();
			Npc npc = npcs[k];
			npcIndices[npcCount++] = k;
			npc.anInt1537 = loopCycle;
			int l = stream.readBits(5);
			if (l > 15)
				l -= 32;
			int i1 = stream.readBits(5);
			if (i1 > 15)
				i1 -= 32;
			int j1 = stream.readBits(1);
			npc.desc = NpcDefinition.lookup(stream
					.readBits(Configuration.npcBits));
			int k1 = stream.readBits(1);
			if (k1 == 1)
				anIntArray894[anInt893++] = k;
			npc.boundDim = npc.desc.boundDim;
			npc.degreesToTurn = npc.desc.degreesToTurn;
			npc.walkAnimIndex = npc.desc.walkAnim;
			npc.turn180AnimIndex = npc.desc.turn180AnimIndex;
			npc.turn90CWAnimIndex = npc.desc.turn90CWAnimIndex;
			npc.turn90CCWAnimIndex = npc.desc.turn90CCWAnimIndex;
			npc.standAnimIndex = npc.desc.standAnim;
			npc.setPos(localPlayer.pathX[0] + i1, localPlayer.pathY[0] + l,
					j1 == 1);
		}
		stream.finishBitAccess();
	}

	public void processGameLoop() {
		if (rsAlreadyLoaded || loadingError || genericLoadingError)
			return;
		loopCycle++;
		if (!loggedIn)
			processLoginScreenInput();
		else
			mainGameProcessor();
		processOnDemandQueue();
	}

	private void showOtherPlayers(boolean flag) {
		if (localPlayer.x >> 7 == destinationX && localPlayer.y >> 7 == destY)
			destinationX = 0;
		int j = playerCount;
		if (flag)
			j = 1;
		for (int l = 0; l < j; l++) {
			Player player;
			int i1;
			if (flag) {
				player = localPlayer;
				i1 = internalLocalPlayerIndex << 14;
			} else {
				player = players[playerIndices[l]];
				i1 = playerIndices[l] << 14;
			}
			if (player == null || !player.isVisible())
				continue;
			player.aBoolean1699 = (lowMem && playerCount > 50 || playerCount > 200)
					&& !flag
					&& player.movementAnimation == player.standAnimIndex;
			int j1 = player.x >> 7;
			int k1 = player.y >> 7;
			if (j1 < 0 || j1 >= 104 || k1 < 0 || k1 >= 104)
				continue;
			if (player.aModel_1714 != null && loopCycle >= player.anInt1707
					&& loopCycle < player.anInt1708) {
				player.aBoolean1699 = false;
				player.anInt1709 = method42(plane, player.y, player.x);
				worldController.method286(plane, player.y, player,
						player.anInt1552, player.anInt1722, player.x,
						player.anInt1709, player.anInt1719, player.anInt1721,
						i1, player.anInt1720);
				continue;
			}
			if ((player.x & 0x7f) == 64 && (player.y & 0x7f) == 64) {
				if (anIntArrayArray929[j1][k1] == anInt1265)
					continue;
				anIntArrayArray929[j1][k1] = anInt1265;
			}
			player.anInt1709 = method42(plane, player.y, player.x);
			worldController.method285(plane, player.anInt1552,
					player.anInt1709, i1, player.y, 60, player.x, player,
					player.aBoolean1541);
		}
	}

	private boolean promptUserForInput(Widget class9) {
		int j = class9.contentType;
		if (friendServerStatus == 2) {
			if (j == 201) {
				inputTaken = true;
				inputDialogState = 0;
				messagePromptRaised = true;
				promptInput = "";
				friendsListAction = 1;
				aString1121 = "Enter name of friend to add to list";
			}
			if (j == 202) {
				inputTaken = true;
				inputDialogState = 0;
				messagePromptRaised = true;
				promptInput = "";
				friendsListAction = 2;
				aString1121 = "Enter name of friend to delete from list";
			}
		}
		if (j == 205) {
			anInt1011 = 250;
			return true;
		}
		if (j == 501) {
			inputTaken = true;
			inputDialogState = 0;
			messagePromptRaised = true;
			promptInput = "";
			friendsListAction = 4;
			aString1121 = "Enter name of player to add to list";
		}
		if (j == 502) {
			inputTaken = true;
			inputDialogState = 0;
			messagePromptRaised = true;
			promptInput = "";
			friendsListAction = 5;
			aString1121 = "Enter name of player to delete from list";
		}
		if (j == 550) {
			inputTaken = true;
			inputDialogState = 0;
			messagePromptRaised = true;
			promptInput = "";
			friendsListAction = 6;
			aString1121 = "Enter the name of the chat you wish to join";
		}
		if (j >= 300 && j <= 313) {
			int k = (j - 300) / 2;
			int j1 = j & 1;
			int i2 = anIntArray1065[k];
			if (i2 != -1) {
				do {
					if (j1 == 0 && --i2 < 0)
						i2 = IdentityKit.length - 1;
					if (j1 == 1 && ++i2 >= IdentityKit.length)
						i2 = 0;
				} while (IdentityKit.cache[i2].aBoolean662
						|| IdentityKit.cache[i2].anInt657 != k
								+ (maleCharacter ? 0 : 7));
				anIntArray1065[k] = i2;
				aBoolean1031 = true;
			}
		}
		if (j >= 314 && j <= 323) {
			int l = (j - 314) / 2;
			int k1 = j & 1;
			int j2 = characterDesignColours[l];
			if (k1 == 0 && --j2 < 0)
				j2 = anIntArrayArray1003[l].length - 1;
			if (k1 == 1 && ++j2 >= anIntArrayArray1003[l].length)
				j2 = 0;
			characterDesignColours[l] = j2;
			aBoolean1031 = true;
		}
		if (j == 324 && !maleCharacter) {
			maleCharacter = true;
			changeCharacterGender();
		}
		if (j == 325 && maleCharacter) {
			maleCharacter = false;
			changeCharacterGender();
		}
		if (j == 326) {
			outgoing.createFrame(101);
			outgoing.writeByte(maleCharacter ? 0 : 1);
			for (int i1 = 0; i1 < 7; i1++)
				outgoing.writeByte(anIntArray1065[i1]);

			for (int l1 = 0; l1 < 5; l1++)
				outgoing.writeByte(characterDesignColours[l1]);

			return true;
		}
		if (j == 613)
			canMute = !canMute;
		if (j >= 601 && j <= 612) {
			clearTopInterfaces();
			if (reportAbuseInput.length() > 0) {
				outgoing.createFrame(218);
				outgoing.writeLong(TextClass.longForName(reportAbuseInput));
				outgoing.writeByte(j - 601);
				outgoing.writeByte(canMute ? 1 : 0);
			}
		}
		return false;
	}

	private void refreshUpdateMasks(Buffer stream) {
		for (int j = 0; j < anInt893; j++) {
			int k = anIntArray894[j];
			Player player = players[k];
			int l = stream.readUnsignedByte();
			if ((l & 0x40) != 0)
				l += stream.readUnsignedByte() << 8;
			appendPlayerUpdateMask(l, k, stream, player);
		}
	}

	private void drawMapScenes(int i, int k, int l, int i1, int j1) {
		int k1 = worldController.method300(j1, l, i);
		if (k1 != 0) {
			int l1 = worldController.method304(j1, l, i, k1);
			int k2 = l1 >> 6 & 3;
			int i3 = l1 & 0x1f;
			int k3 = k;
			if (k1 > 0)
				k3 = i1;
			int ai[] = minimapImage.myPixels;
			int k4 = 24624 + l * 4 + (103 - i) * 512 * 4;
			int i5 = k1 >> 14 & 0x7fff;
			ObjectDefinition class46_2 = ObjectDefinition.lookup(i5);
			if (class46_2.mapscene != -1) {
				Background background_2 = mapScenes[class46_2.mapscene];
				if (background_2 != null) {
					int i6 = (class46_2.width * 4 - background_2.anInt1452) / 2;
					int j6 = (class46_2.length * 4 - background_2.anInt1453) / 2;
					background_2.drawBackground(48 + l * 4 + i6, 48
							+ (104 - i - class46_2.length) * 4 + j6);
				}
			} else {
				if (i3 == 0 || i3 == 2)
					if (k2 == 0) {
						ai[k4] = k3;
						ai[k4 + 512] = k3;
						ai[k4 + 1024] = k3;
						ai[k4 + 1536] = k3;
					} else if (k2 == 1) {
						ai[k4] = k3;
						ai[k4 + 1] = k3;
						ai[k4 + 2] = k3;
						ai[k4 + 3] = k3;
					} else if (k2 == 2) {
						ai[k4 + 3] = k3;
						ai[k4 + 3 + 512] = k3;
						ai[k4 + 3 + 1024] = k3;
						ai[k4 + 3 + 1536] = k3;
					} else if (k2 == 3) {
						ai[k4 + 1536] = k3;
						ai[k4 + 1536 + 1] = k3;
						ai[k4 + 1536 + 2] = k3;
						ai[k4 + 1536 + 3] = k3;
					}
				if (i3 == 3)
					if (k2 == 0)
						ai[k4] = k3;
					else if (k2 == 1)
						ai[k4 + 3] = k3;
					else if (k2 == 2)
						ai[k4 + 3 + 1536] = k3;
					else if (k2 == 3)
						ai[k4 + 1536] = k3;
				if (i3 == 2)
					if (k2 == 3) {
						ai[k4] = k3;
						ai[k4 + 512] = k3;
						ai[k4 + 1024] = k3;
						ai[k4 + 1536] = k3;
					} else if (k2 == 0) {
						ai[k4] = k3;
						ai[k4 + 1] = k3;
						ai[k4 + 2] = k3;
						ai[k4 + 3] = k3;
					} else if (k2 == 1) {
						ai[k4 + 3] = k3;
						ai[k4 + 3 + 512] = k3;
						ai[k4 + 3 + 1024] = k3;
						ai[k4 + 3 + 1536] = k3;
					} else if (k2 == 2) {
						ai[k4 + 1536] = k3;
						ai[k4 + 1536 + 1] = k3;
						ai[k4 + 1536 + 2] = k3;
						ai[k4 + 1536 + 3] = k3;
					}
			}
		}
		k1 = worldController.method302(j1, l, i);
		if (k1 != 0) {
			int i2 = worldController.method304(j1, l, i, k1);
			int l2 = i2 >> 6 & 3;
			int j3 = i2 & 0x1f;
			int l3 = k1 >> 14 & 0x7fff;
			ObjectDefinition class46_1 = ObjectDefinition.lookup(l3);
			if (class46_1.mapscene != -1) {
				Background background_1 = mapScenes[class46_1.mapscene];
				if (background_1 != null) {
					int j5 = (class46_1.width * 4 - background_1.anInt1452) / 2;
					int k5 = (class46_1.length * 4 - background_1.anInt1453) / 2;
					background_1.drawBackground(48 + l * 4 + j5, 48
							+ (104 - i - class46_1.length) * 4 + k5);
				}
			} else if (j3 == 9) {
				int l4 = 0xeeeeee;
				if (k1 > 0)
					l4 = 0xee0000;
				int ai1[] = minimapImage.myPixels;
				int l5 = 24624 + l * 4 + (103 - i) * 512 * 4;
				if (l2 == 0 || l2 == 2) {
					ai1[l5 + 1536] = l4;
					ai1[l5 + 1024 + 1] = l4;
					ai1[l5 + 512 + 2] = l4;
					ai1[l5 + 3] = l4;
				} else {
					ai1[l5] = l4;
					ai1[l5 + 512 + 1] = l4;
					ai1[l5 + 1024 + 2] = l4;
					ai1[l5 + 1536 + 3] = l4;
				}
			}
		}
		k1 = worldController.method303(j1, l, i);
		if (k1 != 0) {
			int j2 = k1 >> 14 & 0x7fff;
			ObjectDefinition class46 = ObjectDefinition.lookup(j2);
			if (class46.mapscene != -1) {
				Background background = mapScenes[class46.mapscene];
				if (background != null) {
					int i4 = (class46.width * 4 - background.anInt1452) / 2;
					int j4 = (class46.length * 4 - background.anInt1453) / 2;
					background.drawBackground(48 + l * 4 + i4, 48
							+ (104 - i - class46.length) * 4 + j4);
				}
			}
		}
	}

	private void loadTitleScreen() {
		aBackground_966 = new Background(titleStreamLoader, "titlebox", 0);
		aBackground_967 = new Background(titleStreamLoader, "titlebutton", 0);
		aBackgroundArray1152s = new Background[12];
		int j = 0;
		try {
			j = Integer.parseInt(getParameter("fl_icon"));
		} catch (Exception _ex) {
		}
		if (j == 0) {
			for (int k = 0; k < 12; k++)
				aBackgroundArray1152s[k] = new Background(titleStreamLoader,
						"runes", k);

		} else {
			for (int l = 0; l < 12; l++)
				aBackgroundArray1152s[l] = new Background(titleStreamLoader,
						"runes", 12 + (l & 3));

		}
		aClass30_Sub2_Sub1_Sub1_1201 = new Sprite(128, 265);
		aClass30_Sub2_Sub1_Sub1_1202 = new Sprite(128, 265);
		System.arraycopy(flameLeftBackground.canvasRaster, 0,
				aClass30_Sub2_Sub1_Sub1_1201.myPixels, 0, 33920);

		System.arraycopy(flameRightBackground.canvasRaster, 0,
				aClass30_Sub2_Sub1_Sub1_1202.myPixels, 0, 33920);

		anIntArray851 = new int[256];
		for (int k1 = 0; k1 < 64; k1++)
			anIntArray851[k1] = k1 * 0x40000;

		for (int l1 = 0; l1 < 64; l1++)
			anIntArray851[l1 + 64] = 0xff0000 + 1024 * l1;

		for (int i2 = 0; i2 < 64; i2++)
			anIntArray851[i2 + 128] = 0xffff00 + 4 * i2;

		for (int j2 = 0; j2 < 64; j2++)
			anIntArray851[j2 + 192] = 0xffffff;

		anIntArray852 = new int[256];
		for (int k2 = 0; k2 < 64; k2++)
			anIntArray852[k2] = k2 * 1024;

		for (int l2 = 0; l2 < 64; l2++)
			anIntArray852[l2 + 64] = 65280 + 4 * l2;

		for (int i3 = 0; i3 < 64; i3++)
			anIntArray852[i3 + 128] = 65535 + 0x40000 * i3;

		for (int j3 = 0; j3 < 64; j3++)
			anIntArray852[j3 + 192] = 0xffffff;

		anIntArray853 = new int[256];
		for (int k3 = 0; k3 < 64; k3++)
			anIntArray853[k3] = k3 * 4;

		for (int l3 = 0; l3 < 64; l3++)
			anIntArray853[l3 + 64] = 255 + 0x40000 * l3;

		for (int i4 = 0; i4 < 64; i4++)
			anIntArray853[i4 + 128] = 0xff00ff + 1024 * i4;

		for (int j4 = 0; j4 < 64; j4++)
			anIntArray853[j4 + 192] = 0xffffff;

		anIntArray850 = new int[256];
		anIntArray1190 = new int[32768];
		anIntArray1191 = new int[32768];
		randomizeBackground(null);
		anIntArray828 = new int[32768];
		anIntArray829 = new int[32768];
		drawLoadingText(10, "Connecting to fileserver");
		if (!aBoolean831) {
			drawFlames = true;
			aBoolean831 = true;
			startRunnable(this, 2);
		}
	}

	private static void setHighMem() {
		SceneGraph.lowMem = false;
		Rasterizer.lowMem = false;
		lowMem = false;
		ObjectManager.lowMem = false;
		ObjectDefinition.lowMemory = false;
	}

	public static void main(String args[]) {
		try {
			nodeID = 10;
			portOff = 0;
			setHighMem();
			isMembers = true;
			Signlink.storeid = 32;
			Signlink.startpriv(InetAddress.getLocalHost());
			frameMode(ScreenMode.FIXED);
			instance = new Game();
			instance.createClientFrame(frameWidth, frameHeight);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static Game instance;

	private void loadingStages() {
		if (lowMem && loadingStage == 2 && ObjectManager.anInt131 != plane) {
			gameScreenImageProducer.initDrawingArea();
			drawLoadingMessages(1, "Loading - please wait.", null);
			gameScreenImageProducer.drawGraphics(
					frameMode == ScreenMode.FIXED ? 4 : 0, super.graphics,
					frameMode == ScreenMode.FIXED ? 4 : 0);
			loadingStage = 1;
			aLong824 = System.currentTimeMillis();
		}
		if (loadingStage == 1) {
			int j = method54();
			if (j != 0 && System.currentTimeMillis() - aLong824 > 0x57e40L) {
				Signlink.reporterror(myUsername + " glcfb " + serverSeed + ","
						+ j + "," + lowMem + "," + decompressors[0] + ","
						+ onDemandFetcher.getNodeCount() + "," + plane + ","
						+ anInt1069 + "," + anInt1070);
				aLong824 = System.currentTimeMillis();
			}
		}
		if (loadingStage == 2 && plane != anInt985) {
			anInt985 = plane;
			renderMapScene(plane);
		}
	}

	private int method54() {
		for (int i = 0; i < aByteArrayArray1183.length; i++) {
			if (aByteArrayArray1183[i] == null && anIntArray1235[i] != -1)
				return -1;
			if (aByteArrayArray1247[i] == null && anIntArray1236[i] != -1)
				return -2;
		}
		boolean flag = true;
		for (int j = 0; j < aByteArrayArray1183.length; j++) {
			byte abyte0[] = aByteArrayArray1247[j];
			if (abyte0 != null) {
				int k = (anIntArray1234[j] >> 8) * 64 - baseX;
				int l = (anIntArray1234[j] & 0xff) * 64 - baseY;
				if (aBoolean1159) {
					k = 10;
					l = 10;
				}
				flag &= ObjectManager.method189(k, abyte0, l);
			}
		}
		if (!flag)
			return -3;
		if (aBoolean1080) {
			return -4;
		} else {
			loadingStage = 2;
			ObjectManager.anInt131 = plane;
			updateWorldObjects();
			outgoing.createFrame(121);
			return 0;
		}
	}

	private void createProjectiles() {
		for (SceneProjectile class30_sub2_sub4_sub4 = (SceneProjectile) projectiles
				.reverseGetFirst(); class30_sub2_sub4_sub4 != null; class30_sub2_sub4_sub4 = (SceneProjectile) projectiles
				.reverseGetNext())
			if (class30_sub2_sub4_sub4.anInt1597 != plane
					|| loopCycle > class30_sub2_sub4_sub4.anInt1572)
				class30_sub2_sub4_sub4.unlink();
			else if (loopCycle >= class30_sub2_sub4_sub4.anInt1571) {
				if (class30_sub2_sub4_sub4.anInt1590 > 0) {
					Npc npc = npcs[class30_sub2_sub4_sub4.anInt1590 - 1];
					if (npc != null && npc.x >= 0 && npc.x < 13312
							&& npc.y >= 0 && npc.y < 13312)
						class30_sub2_sub4_sub4.method455(
								loopCycle,
								npc.y,
								method42(class30_sub2_sub4_sub4.anInt1597,
										npc.y, npc.x)
										- class30_sub2_sub4_sub4.anInt1583,
								npc.x);
				}
				if (class30_sub2_sub4_sub4.anInt1590 < 0) {
					int j = -class30_sub2_sub4_sub4.anInt1590 - 1;
					Player player;
					if (j == unknownInt10)
						player = localPlayer;
					else
						player = players[j];
					if (player != null && player.x >= 0 && player.x < 13312
							&& player.y >= 0 && player.y < 13312)
						class30_sub2_sub4_sub4.method455(
								loopCycle,
								player.y,
								method42(class30_sub2_sub4_sub4.anInt1597,
										player.y, player.x)
										- class30_sub2_sub4_sub4.anInt1583,
								player.x);
				}
				class30_sub2_sub4_sub4.method456(anInt945);
				worldController.method285(plane,
						class30_sub2_sub4_sub4.anInt1595,
						(int) class30_sub2_sub4_sub4.aDouble1587, -1,
						(int) class30_sub2_sub4_sub4.aDouble1586, 60,
						(int) class30_sub2_sub4_sub4.aDouble1585,
						class30_sub2_sub4_sub4, false);
			}

	}

	public AppletContext getAppletContext() {
		if (Signlink.mainapp != null)
			return Signlink.mainapp.getAppletContext();
		else
			return super.getAppletContext();
	}

	private void drawLogo() {
		byte abyte0[] = titleStreamLoader.getDataForName("title.dat");
		Sprite sprite = new Sprite(abyte0, this);
		flameLeftBackground.initDrawingArea();
		sprite.method346(0, 0);
		flameRightBackground.initDrawingArea();
		sprite.method346(-637, 0);
		topLeft1BackgroundTile.initDrawingArea();
		sprite.method346(-128, 0);
		bottomLeft1BackgroundTile.initDrawingArea();
		sprite.method346(-202, -371);
		loginBoxImageProducer.initDrawingArea();
		sprite.method346(-202, -171);
		loginScreenAccessories.initDrawingArea();
		sprite.method346(0, -400);
		bottomLeft0BackgroundTile.initDrawingArea();
		sprite.method346(0, -265);
		bottomRightImageProducer.initDrawingArea();
		sprite.method346(-562, -265);
		loginMusicImageProducer.initDrawingArea();
		sprite.method346(-562, -265);
		middleLeft1BackgroundTile.initDrawingArea();
		sprite.method346(-128, -171);
		aRSImageProducer_1115.initDrawingArea();
		sprite.method346(-562, -171);
		int ai[] = new int[sprite.myWidth];
		for (int j = 0; j < sprite.myHeight; j++) {
			for (int k = 0; k < sprite.myWidth; k++)
				ai[k] = sprite.myPixels[(sprite.myWidth - k - 1)
						+ sprite.myWidth * j];

			System.arraycopy(ai, 0, sprite.myPixels, sprite.myWidth * j,
					sprite.myWidth);
		}
		flameLeftBackground.initDrawingArea();
		sprite.method346(382, 0);
		flameRightBackground.initDrawingArea();
		sprite.method346(-255, 0);
		topLeft1BackgroundTile.initDrawingArea();
		sprite.method346(254, 0);
		bottomLeft1BackgroundTile.initDrawingArea();
		sprite.method346(180, -371);
		loginBoxImageProducer.initDrawingArea();
		sprite.method346(180, -171);
		bottomLeft0BackgroundTile.initDrawingArea();
		sprite.method346(382, -265);
		bottomRightImageProducer.initDrawingArea();
		sprite.method346(-180, -265);
		loginMusicImageProducer.initDrawingArea();
		sprite.method346(-180, -265);
		middleLeft1BackgroundTile.initDrawingArea();
		sprite.method346(254, -171);
		aRSImageProducer_1115.initDrawingArea();
		sprite.method346(-180, -171);
		sprite = new Sprite(titleStreamLoader, "logo", 0);
		topLeft1BackgroundTile.initDrawingArea();
		sprite.drawSprite(382 - sprite.myWidth / 2 - 128, 18);
		sprite = null;
		System.gc();
	}

	private void processOnDemandQueue() {
		do {
			OnDemandNode onDemandData;
			do {
				onDemandData = onDemandFetcher.getNextNode();
				if (onDemandData == null)
					return;
				if (onDemandData.dataType == 0) {
					Model.method460(onDemandData.buffer, onDemandData.ID);
					if (backDialogueId != -1)
						inputTaken = true;
				}
				if (onDemandData.dataType == 1) {
					SequenceFrame.load(onDemandData.buffer, onDemandData.ID);
				}
				if (onDemandData.dataType == 2 && onDemandData.ID == nextSong
						&& onDemandData.buffer != null)
					saveMidi(songChanging, onDemandData.buffer);
				if (onDemandData.dataType == 3 && loadingStage == 1) {
					for (int i = 0; i < aByteArrayArray1183.length; i++) {
						if (anIntArray1235[i] == onDemandData.ID) {
							aByteArrayArray1183[i] = onDemandData.buffer;
							if (onDemandData.buffer == null)
								anIntArray1235[i] = -1;
							break;
						}
						if (anIntArray1236[i] != onDemandData.ID)
							continue;
						aByteArrayArray1247[i] = onDemandData.buffer;
						if (onDemandData.buffer == null)
							anIntArray1236[i] = -1;
						break;
					}

				}
			} while (onDemandData.dataType != 93
					|| !onDemandFetcher.method564(onDemandData.ID));
			ObjectManager.method173(new Buffer(onDemandData.buffer),
					onDemandFetcher);
		} while (true);
	}

	private void calcFlamesPosition() {
		char c = '\u0100';
		for (int j = 10; j < 117; j++) {
			int k = (int) (Math.random() * 100D);
			if (k < 50)
				anIntArray828[j + (c - 2 << 7)] = 255;
		}
		for (int l = 0; l < 100; l++) {
			int i1 = (int) (Math.random() * 124D) + 2;
			int k1 = (int) (Math.random() * 128D) + 128;
			int k2 = i1 + (k1 << 7);
			anIntArray828[k2] = 192;
		}

		for (int j1 = 1; j1 < c - 1; j1++) {
			for (int l1 = 1; l1 < 127; l1++) {
				int l2 = l1 + (j1 << 7);
				anIntArray829[l2] = (anIntArray828[l2 - 1]
						+ anIntArray828[l2 + 1] + anIntArray828[l2 - 128] + anIntArray828[l2 + 128]) / 4;
			}

		}

		anInt1275 += 128;
		if (anInt1275 > anIntArray1190.length) {
			anInt1275 -= anIntArray1190.length;
			int i2 = (int) (Math.random() * 12D);
			randomizeBackground(aBackgroundArray1152s[i2]);
		}
		for (int j2 = 1; j2 < c - 1; j2++) {
			for (int i3 = 1; i3 < 127; i3++) {
				int k3 = i3 + (j2 << 7);
				int i4 = anIntArray829[k3 + 128]
						- anIntArray1190[k3 + anInt1275 & anIntArray1190.length
								- 1] / 5;
				if (i4 < 0)
					i4 = 0;
				anIntArray828[k3] = i4;
			}

		}

		System.arraycopy(anIntArray969, 1, anIntArray969, 0, c - 1);

		anIntArray969[c - 1] = (int) (Math.sin((double) loopCycle / 14D) * 16D
				+ Math.sin((double) loopCycle / 15D) * 14D + Math
				.sin((double) loopCycle / 16D) * 12D);
		if (anInt1040 > 0)
			anInt1040 -= 4;
		if (anInt1041 > 0)
			anInt1041 -= 4;
		if (anInt1040 == 0 && anInt1041 == 0) {
			int l3 = (int) (Math.random() * 2000D);
			if (l3 == 0)
				anInt1040 = 1024;
			if (l3 == 1)
				anInt1041 = 1024;
		}
	}

	private void writeInterface(int i) {
		Widget class9 = Widget.interfaceCache[i];
		for (int j = 0; j < class9.children.length; j++) {
			if (class9.children[j] == -1)
				break;
			Widget class9_1 = Widget.interfaceCache[class9.children[j]];
			if (class9_1.type == 1)
				writeInterface(class9_1.id);
			class9_1.anInt246 = 0;
			class9_1.anInt208 = 0;
		}
	}

	private void drawHeadIcon() {
		if (hintIconDrawType != 2)
			return;
		calcEntityScreenPos((hintIconX - baseX << 7) + anInt937, anInt936 * 2,
				(hintIconY - baseY << 7) + anInt938);
		if (spriteDrawX > -1 && loopCycle % 20 < 10)
			headIconsHint[0].drawSprite(spriteDrawX - 12, spriteDrawY - 28);
	}

	private void mainGameProcessor() {
		refreshFrameSize();
		if (systemUpdateTime > 1)
			systemUpdateTime--;
		if (anInt1011 > 0)
			anInt1011--;
		for (int j = 0; j < 5; j++)
			if (!parsePacket())
				break;

		if (!loggedIn)
			return;
		synchronized (mouseDetection.syncObject) {
			if (flagged) {
				if (super.clickMode3 != 0 || mouseDetection.coordsIndex >= 40) {
					outgoing.createFrame(45);
					outgoing.writeByte(0);
					int j2 = outgoing.currentPosition;
					int j3 = 0;
					for (int j4 = 0; j4 < mouseDetection.coordsIndex; j4++) {
						if (j2 - outgoing.currentPosition >= 240)
							break;
						j3++;
						int l4 = mouseDetection.coordsY[j4];
						if (l4 < 0)
							l4 = 0;
						else if (l4 > 502)
							l4 = 502;
						int k5 = mouseDetection.coordsX[j4];
						if (k5 < 0)
							k5 = 0;
						else if (k5 > 764)
							k5 = 764;
						int i6 = l4 * 765 + k5;
						if (mouseDetection.coordsY[j4] == -1
								&& mouseDetection.coordsX[j4] == -1) {
							k5 = -1;
							l4 = -1;
							i6 = 0x7ffff;
						}
						if (k5 == anInt1237 && l4 == anInt1238) {
							if (duplicateClickCount < 2047)
								duplicateClickCount++;
						} else {
							int j6 = k5 - anInt1237;
							anInt1237 = k5;
							int k6 = l4 - anInt1238;
							anInt1238 = l4;
							if (duplicateClickCount < 8 && j6 >= -32
									&& j6 <= 31 && k6 >= -32 && k6 <= 31) {
								j6 += 32;
								k6 += 32;
								outgoing.writeShort((duplicateClickCount << 12)
										+ (j6 << 6) + k6);
								duplicateClickCount = 0;
							} else if (duplicateClickCount < 8) {
								outgoing.writeTriByte(0x800000
										+ (duplicateClickCount << 19) + i6);
								duplicateClickCount = 0;
							} else {
								outgoing.writeInt(0xc0000000
										+ (duplicateClickCount << 19) + i6);
								duplicateClickCount = 0;
							}
						}
					}

					outgoing.writeBytes(outgoing.currentPosition - j2);
					if (j3 >= mouseDetection.coordsIndex) {
						mouseDetection.coordsIndex = 0;
					} else {
						mouseDetection.coordsIndex -= j3;
						for (int i5 = 0; i5 < mouseDetection.coordsIndex; i5++) {
							mouseDetection.coordsX[i5] = mouseDetection.coordsX[i5
									+ j3];
							mouseDetection.coordsY[i5] = mouseDetection.coordsY[i5
									+ j3];
						}

					}
				}
			} else {
				mouseDetection.coordsIndex = 0;
			}
		}
		if (super.clickMode3 != 0) {
			long l = (super.aLong29 - aLong1220) / 50L;
			if (l > 4095L)
				l = 4095L;
			aLong1220 = super.aLong29;
			int k2 = super.saveClickY;
			if (k2 < 0)
				k2 = 0;
			else if (k2 > 502)
				k2 = 502;
			int k3 = super.saveClickX;
			if (k3 < 0)
				k3 = 0;
			else if (k3 > 764)
				k3 = 764;
			int k4 = k2 * 765 + k3;
			int j5 = 0;
			if (super.clickMode3 == 2)
				j5 = 1;
			int l5 = (int) l;
			outgoing.createFrame(241);
			outgoing.writeInt((l5 << 20) + (j5 << 19) + k4);
		}
		if (anInt1016 > 0)
			anInt1016--;
		if (super.keyArray[1] == 1 || super.keyArray[2] == 1
				|| super.keyArray[3] == 1 || super.keyArray[4] == 1)
			aBoolean1017 = true;
		if (aBoolean1017 && anInt1016 <= 0) {
			anInt1016 = 20;
			aBoolean1017 = false;
			outgoing.createFrame(86);
			outgoing.writeShort(anInt1184);
			outgoing.writeShortA(cameraHorizontal);
		}
		if (super.awtFocus && !aBoolean954) {
			aBoolean954 = true;
			outgoing.createFrame(3);
			outgoing.writeByte(1);
		}
		if (!super.awtFocus && aBoolean954) {
			aBoolean954 = false;
			outgoing.createFrame(3);
			outgoing.writeByte(0);
		}
		loadingStages();
		method115();
		timeoutCounter++;
		if (timeoutCounter > 750)
			dropClient();
		updatePlayerInstances();
		forceNPCUpdateBlock();
		processTrackUpdates();
		resetSpokenText();
		anInt945++;
		if (crossType != 0) {
			crossIndex += 20;
			if (crossIndex >= 400)
				crossType = 0;
		}
		if (atInventoryInterfaceType != 0) {
			atInventoryLoopCycle++;
			if (atInventoryLoopCycle >= 15) {
				if (atInventoryInterfaceType == 2) {
				}
				if (atInventoryInterfaceType == 3)
					inputTaken = true;
				atInventoryInterfaceType = 0;
			}
		}
		if (activeInterfaceType != 0) {
			anInt989++;
			if (super.mouseX > anInt1087 + 5 || super.mouseX < anInt1087 - 5
					|| super.mouseY > anInt1088 + 5
					|| super.mouseY < anInt1088 - 5)
				aBoolean1242 = true;
			if (super.clickMode2 == 0) {
				if (activeInterfaceType == 2) {
				}
				if (activeInterfaceType == 3)
					inputTaken = true;
				activeInterfaceType = 0;
				if (aBoolean1242 && anInt989 >= 15) {
					lastActiveInvInterface = -1;
					processRightClick();
					if (lastActiveInvInterface == anInt1084
							&& mouseInvInterfaceIndex != anInt1085) {
						Widget childInterface = Widget.interfaceCache[anInt1084];
						int j1 = 0;
						if (anInt913 == 1 && childInterface.contentType == 206)
							j1 = 1;
						if (childInterface.inventoryItemId[mouseInvInterfaceIndex] <= 0)
							j1 = 0;
						if (childInterface.replaceItems) {
							int l2 = anInt1085;
							int l3 = mouseInvInterfaceIndex;
							childInterface.inventoryItemId[l3] = childInterface.inventoryItemId[l2];
							childInterface.invStackSizes[l3] = childInterface.invStackSizes[l2];
							childInterface.inventoryItemId[l2] = -1;
							childInterface.invStackSizes[l2] = 0;
						} else if (j1 == 1) {
							int i3 = anInt1085;
							for (int i4 = mouseInvInterfaceIndex; i3 != i4;)
								if (i3 > i4) {
									childInterface.swapInventoryItems(i3,
											i3 - 1);
									i3--;
								} else if (i3 < i4) {
									childInterface.swapInventoryItems(i3,
											i3 + 1);
									i3++;
								}

						} else {
							childInterface.swapInventoryItems(anInt1085,
									mouseInvInterfaceIndex);
						}
						outgoing.createFrame(214);
						outgoing.writeLEShortA(anInt1084);
						outgoing.writeNegatedByte(j1);
						outgoing.writeLEShortA(anInt1085);
						outgoing.writeLEShort(mouseInvInterfaceIndex);
					}
				} else if ((anInt1253 == 1 || menuHasAddFriend(menuActionRow - 1))
						&& menuActionRow > 2)
					determineMenuSize();
				else if (menuActionRow > 0)
					doAction(menuActionRow - 1);
				atInventoryLoopCycle = 10;
				super.clickMode3 = 0;
			}
		}
		if (SceneGraph.anInt470 != -1) {
			int k = SceneGraph.anInt470;
			int k1 = SceneGraph.anInt471;
			boolean flag = doWalkTo(0, 0, 0, 0, localPlayer.pathY[0], 0, 0, k1,
					localPlayer.pathX[0], true, k);
			SceneGraph.anInt470 = -1;
			if (flag) {
				crossX = super.saveClickX;
				crossY = super.saveClickY;
				crossType = 1;
				crossIndex = 0;
			}
		}
		if (super.clickMode3 == 1 && clickToContinueString != null) {
			clickToContinueString = null;
			inputTaken = true;
			super.clickMode3 = 0;
		}
		processMenuClick();
		if (super.clickMode2 == 1 || super.clickMode3 == 1)
			anInt1213++;
		if (anInt1500 != 0 || anInt1044 != 0 || anInt1129 != 0) {
			if (anInt1501 < 0 && !menuOpen) {
				anInt1501++;
				if (anInt1501 == 0) {
					if (anInt1500 != 0) {
						inputTaken = true;
					}
					if (anInt1044 != 0) {
					}
				}
			}
		} else if (anInt1501 > 0) {
			anInt1501--;
		}
		if (loadingStage == 2)
			checkForGameUsages();
		if (loadingStage == 2 && aBoolean1160)
			calcCameraPos();
		for (int i1 = 0; i1 < 5; i1++)
			anIntArray1030[i1]++;

		manageTextInputs();
		super.idleTime++;
		if (super.idleTime > 4500) {
			anInt1011 = 250;
			super.idleTime -= 500;
			outgoing.createFrame(202);
		}
		anInt1010++;
		if (anInt1010 > 50)
			outgoing.createFrame(0);
		try {
			if (socketStream != null && outgoing.currentPosition > 0) {
				socketStream.queueBytes(outgoing.currentPosition,
						outgoing.payload);
				outgoing.currentPosition = 0;
				anInt1010 = 0;
			}
		} catch (IOException _ex) {
			dropClient();
		} catch (Exception exception) {
			resetLogout();
		}
	}

	private void method63() {
		TemporaryObject class30_sub1 = (TemporaryObject) spawns
				.reverseGetFirst();
		for (; class30_sub1 != null; class30_sub1 = (TemporaryObject) spawns
				.reverseGetNext())
			if (class30_sub1.anInt1294 == -1) {
				class30_sub1.anInt1302 = 0;
				method89(class30_sub1);
			} else {
				class30_sub1.unlink();
			}

	}

	private void setupLoginScreen() {
		if (topLeft1BackgroundTile != null)
			return;
		super.fullGameScreen = null;
		chatboxImageProducer = null;
		minimapImageProducer = null;
		tabImageProducer = null;
		gameScreenImageProducer = null;
		chatSettingImageProducer = null;
		flameLeftBackground = new ImageProducer(128, 265);
		Raster.clear();
		flameRightBackground = new ImageProducer(128, 265);
		Raster.clear();
		topLeft1BackgroundTile = new ImageProducer(509, 171);
		Raster.clear();
		bottomLeft1BackgroundTile = new ImageProducer(360, 132);
		Raster.clear();
		loginBoxImageProducer = new ImageProducer(360, 200);
		Raster.clear();
		loginScreenAccessories = new ImageProducer(300, 800);
		Raster.clear();
		bottomLeft0BackgroundTile = new ImageProducer(202, 238);
		Raster.clear();
		bottomRightImageProducer = new ImageProducer(203, 238);
		Raster.clear();
		loginMusicImageProducer = new ImageProducer(203, 238);
		Raster.clear();
		middleLeft1BackgroundTile = new ImageProducer(74, 94);
		Raster.clear();
		aRSImageProducer_1115 = new ImageProducer(75, 94);
		Raster.clear();
		if (titleStreamLoader != null) {
			drawLogo();
			loadTitleScreen();
		}
		welcomeScreenRaised = true;
	}

	void drawLoadingText(int i, String s) {
		anInt1079 = i;
		aString1049 = s;
		setupLoginScreen();
		if (titleStreamLoader == null) {
			super.drawLoadingText(i, s);
			return;
		}
		loginBoxImageProducer.initDrawingArea();
		char c = '\u0168';
		char c1 = '\310';
		byte byte1 = 20;
		boldText.drawText(0xffffff, Configuration.CLIENT_NAME
				+ " is loading - please wait...", c1 / 2 - 26 - byte1, c / 2);
		int j = c1 / 2 - 18 - byte1;
		Raster.fillPixels(c / 2 - 152, 304, 34, 0x8c1111, j);
		Raster.fillPixels(c / 2 - 151, 302, 32, 0, j + 1);
		Raster.drawPixels(30, j + 2, c / 2 - 150, 0x8c1111, i * 3);
		Raster.drawPixels(30, j + 2, (c / 2 - 150) + i * 3, 0, 300 - i * 3);
		boldText.drawText(0xffffff, s, (c1 / 2 + 5) - byte1, c / 2);
		loginBoxImageProducer.drawGraphics(171, super.graphics, 202);
		if (welcomeScreenRaised) {
			welcomeScreenRaised = false;
			if (!aBoolean831) {
				flameLeftBackground.drawGraphics(0, super.graphics, 0);
				flameRightBackground.drawGraphics(0, super.graphics, 637);
			}
			topLeft1BackgroundTile.drawGraphics(0, super.graphics, 128);
			bottomLeft1BackgroundTile.drawGraphics(371, super.graphics, 202);
			bottomLeft0BackgroundTile.drawGraphics(265, super.graphics, 0);
			bottomRightImageProducer.drawGraphics(265, super.graphics, 562);
			loginMusicImageProducer.drawGraphics(265, super.graphics, 562);
			middleLeft1BackgroundTile.drawGraphics(171, super.graphics, 128);
			aRSImageProducer_1115.drawGraphics(171, super.graphics, 562);
		}
	}

	private void method65(int i, int j, int k, int l, Widget class9, int i1,
			boolean flag, int j1) {
		int anInt992;
		if (aBoolean972)
			anInt992 = 32;
		else
			anInt992 = 0;
		aBoolean972 = false;
		if (k >= i && k < i + 16 && l >= i1 && l < i1 + 16) {
			class9.scrollPosition -= anInt1213 * 4;
			if (flag) {
			}
		} else if (k >= i && k < i + 16 && l >= (i1 + j) - 16 && l < i1 + j) {
			class9.scrollPosition += anInt1213 * 4;
			if (flag) {
			}
		} else if (k >= i - anInt992 && k < i + 16 + anInt992 && l >= i1 + 16
				&& l < (i1 + j) - 16 && anInt1213 > 0) {
			int l1 = ((j - 32) * j) / j1;
			if (l1 < 8)
				l1 = 8;
			int i2 = l - i1 - 16 - l1 / 2;
			int j2 = j - 32 - l1;
			class9.scrollPosition = ((j1 - j) * i2) / j2;
			if (flag) {
			}
			aBoolean972 = true;
		}
	}

	private boolean clickObject(int i, int j, int k) {
		int i1 = i >> 14 & 0x7fff;
		int j1 = worldController.method304(plane, k, j, i);
		if (j1 == -1)
			return false;
		int k1 = j1 & 0x1f;
		int l1 = j1 >> 6 & 3;
		if (k1 == 10 || k1 == 11 || k1 == 22) {
			ObjectDefinition class46 = ObjectDefinition.lookup(i1);
			int i2;
			int j2;
			if (l1 == 0 || l1 == 2) {
				i2 = class46.width;
				j2 = class46.length;
			} else {
				i2 = class46.length;
				j2 = class46.width;
			}
			int k2 = class46.surroundings;
			if (l1 != 0)
				k2 = (k2 << l1 & 0xf) + (k2 >> 4 - l1);
			doWalkTo(2, 0, j2, 0, localPlayer.pathY[0], i2, k2, j,
					localPlayer.pathX[0], false, k);
		} else {
			doWalkTo(2, l1, 0, k1 + 1, localPlayer.pathY[0], 0, 0, j,
					localPlayer.pathX[0], false, k);
		}
		crossX = super.saveClickX;
		crossY = super.saveClickY;
		crossType = 2;
		crossIndex = 0;
		return true;
	}

	public void playSong(int id) {
		if (id != currentSong && Configuration.enableMusic && !lowMem
				&& prevSong == 0) {
			nextSong = id;
			songChanging = true;
			onDemandFetcher.provide(2, nextSong);
			currentSong = id;
		}
	}

	public void stopMidi() {
		if (Signlink.music != null) {
			Signlink.music.stop();
		}
		Signlink.fadeMidi = 0;
		Signlink.midi = "stop";
	}

	private void adjustVolume(boolean updateMidi, int volume) {
		Signlink.setVolume(volume);
		if (updateMidi) {
			Signlink.midi = "voladjust";
		}
	}

	private int currentTrackTime;
	private long trackTimer;

	private boolean saveWave(byte data[], int id) {
		return data == null || Signlink.wavesave(data, id);
	}

	@SuppressWarnings("unused")
	private int currentTrackLoop;

	private void processTrackUpdates() {
		for (int count = 0; count < trackCount; count++) {
			boolean replay = false;
			try {
				Buffer stream = SoundTrack.data(trackLoops[count],
						tracks[count]);
				new SoundPlayer((InputStream) new ByteArrayInputStream(
						stream.payload, 0, stream.currentPosition),
						soundVolume[count], soundDelay[count]);
				if (System.currentTimeMillis()
						+ (long) (stream.currentPosition / 22) > trackTimer
						+ (long) (currentTrackTime / 22)) {
					currentTrackTime = stream.currentPosition;
					trackTimer = System.currentTimeMillis();
					if (saveWave(stream.payload, stream.currentPosition)) {
						currentTrackPlaying = tracks[count];
						currentTrackLoop = trackLoops[count];
					} else {
						replay = true;
					}
				}
			} catch (Exception exception) {
			}
			if (!replay || soundDelay[count] == -5) {
				trackCount--;
				for (int index = count; index < trackCount; index++) {
					tracks[index] = tracks[index + 1];
					trackLoops[index] = trackLoops[index + 1];
					soundDelay[index] = soundDelay[index + 1];
					soundVolume[index] = soundVolume[index + 1];
				}
				count--;
			} else {
				soundDelay[count] = -5;
			}
		}

		if (prevSong > 0) {
			prevSong -= 20;
			if (prevSong < 0)
				prevSong = 0;
			if (prevSong == 0 && Configuration.enableMusic && !lowMem) {
				nextSong = currentSong;
				songChanging = true;
				onDemandFetcher.provide(2, nextSong);
			}
		}
	}

	private CacheArchive streamLoaderForName(int i, String s, String s1, int j,
			int k) {
		byte abyte0[] = null;
		int l = 5;
		try {
			if (decompressors[0] != null)
				abyte0 = decompressors[0].decompress(i);
		} catch (Exception _ex) {
		}
		if (abyte0 != null) {
			// aCRC32_930.reset();
			// aCRC32_930.update(abyte0);
			// int i1 = (int)aCRC32_930.getValue();
			// if(i1 != j)
		}
		if (abyte0 != null) {
			CacheArchive streamLoader = new CacheArchive(abyte0);
			return streamLoader;
		}
		int j1 = 0;
		while (abyte0 == null) {
			String s2 = "Unknown error";
			drawLoadingText(k, "Requesting " + s);
			try {
				int k1 = 0;
				DataInputStream datainputstream = openJagGrabInputStream(s1 + j);
				byte abyte1[] = new byte[6];
				datainputstream.readFully(abyte1, 0, 6);
				Buffer stream = new Buffer(abyte1);
				stream.currentPosition = 3;
				int i2 = stream.read3Bytes() + 6;
				int j2 = 6;
				abyte0 = new byte[i2];
				System.arraycopy(abyte1, 0, abyte0, 0, 6);

				while (j2 < i2) {
					int l2 = i2 - j2;
					if (l2 > 1000)
						l2 = 1000;
					int j3 = datainputstream.read(abyte0, j2, l2);
					if (j3 < 0) {
						s2 = "Length error: " + j2 + "/" + i2;
						throw new IOException("EOF");
					}
					j2 += j3;
					int k3 = (j2 * 100) / i2;
					if (k3 != k1)
						drawLoadingText(k, "Loading " + s + " - " + k3 + "%");
					k1 = k3;
				}
				datainputstream.close();
				try {
					if (decompressors[0] != null)
						decompressors[0].method234(abyte0.length, abyte0, i);
				} catch (Exception _ex) {
					decompressors[0] = null;
				}
				/*
				 * if(abyte0 != null) { aCRC32_930.reset();
				 * aCRC32_930.update(abyte0); int i3 =
				 * (int)aCRC32_930.getValue(); if(i3 != j) { abyte0 = null;
				 * j1++; s2 = "Checksum error: " + i3; } }
				 */
			} catch (IOException ioexception) {
				if (s2.equals("Unknown error"))
					s2 = "Connection error";
				abyte0 = null;
			} catch (NullPointerException _ex) {
				s2 = "Null error";
				abyte0 = null;
				if (!Signlink.reporterror)
					return null;
			} catch (ArrayIndexOutOfBoundsException _ex) {
				s2 = "Bounds error";
				abyte0 = null;
				if (!Signlink.reporterror)
					return null;
			} catch (Exception _ex) {
				s2 = "Unexpected error";
				abyte0 = null;
				if (!Signlink.reporterror)
					return null;
			}
			if (abyte0 == null) {
				for (int l1 = l; l1 > 0; l1--) {
					if (j1 >= 3) {
						drawLoadingText(k, "Game updated - please reload page");
						l1 = 10;
					} else {
						drawLoadingText(k, s2 + " - Retrying in " + l1);
					}
					try {
						Thread.sleep(1000L);
					} catch (Exception _ex) {
					}
				}

				l *= 2;
				if (l > 60)
					l = 60;
				aBoolean872 = !aBoolean872;
			}

		}

		CacheArchive streamLoader_1 = new CacheArchive(abyte0);
		return streamLoader_1;
	}

	private void dropClient() {
		if (anInt1011 > 0) {
			resetLogout();
			return;
		}
		Raster.fillPixels(2, 229, 39, 0xffffff, 2); // white box around
		Raster.drawPixels(37, 3, 3, 0, 227); // black fill
		regularText.drawText(0, "Connection lost.", 19, 120);
		regularText.drawText(0xffffff, "Connection lost.", 18, 119);
		regularText.drawText(0, "Please wait - attempting to reestablish.", 34,
				117);
		regularText.drawText(0xffffff,
				"Please wait - attempting to reestablish.", 34, 116);
		gameScreenImageProducer.drawGraphics(frameMode == ScreenMode.FIXED ? 4
				: 0, super.graphics, frameMode == ScreenMode.FIXED ? 4 : 0);
		minimapState = 0;
		destinationX = 0;
		RSSocket rsSocket = socketStream;
		loggedIn = false;
		loginFailures = 0;
		login(myUsername, myPassword, true);
		if (!loggedIn)
			resetLogout();
		try {
			rsSocket.close();
		} catch (Exception _ex) {
		}
	}

	public void setNorth() {
		anInt1278 = 0;
		anInt1131 = 0;
		anInt896 = 0;
		cameraHorizontal = 0;
		minimapRotation = 0;
		minimapZoom = 0;
	}

	private void doAction(int i) {
		if (i < 0)
			return;
		if (inputDialogState != 0) {
			inputDialogState = 0;
			inputTaken = true;
		}
		int j = menuActionCmd2[i];
		int index = menuActionCmd3[i];
		int l = menuActionID[i];
		int i1 = menuActionCmd1[i];
		if (l >= 2000)
			l -= 2000;
		if (l == 851) {
			outgoing.createFrame(185);
			outgoing.writeShort(155);
		}
		if (l == 474) {
			counterOn = !counterOn;
		}
		if (l == 700) {
			if (tabInterfaceIDs[10] != -1) {
				if (tabID == 10) {
					showTabComponents = !showTabComponents;
				} else {
					showTabComponents = true;
				}
				tabID = 10;
				tabAreaAltered = true;
			}
		}
		if (l == 475) {
			xpCounter = 0;
		}
		if (l == 696) {
			setNorth();
		}
		if (l == 1506) { // Select quick prayers
			outgoing.createFrame(185);
			outgoing.writeShort(5001);
		}
		if (l == 1500) { // Toggle quick prayers
			prayClicked = !prayClicked;
			outgoing.createFrame(185);
			outgoing.writeShort(5000);
		}
		if (l == 1508) { // Toggle HP above heads
			Configuration.hpAboveHeads = !Configuration.hpAboveHeads;
		}
		if (l == 104) {
			Widget class9_1 = Widget.interfaceCache[index];
			spellID = class9_1.id;
			if (!autocast) {
				autocast = true;
				autoCastId = class9_1.id;
				outgoing.createFrame(185);
				outgoing.writeShort(class9_1.id);
			} else if (autoCastId == class9_1.id) {
				autocast = false;
				autoCastId = 0;
				outgoing.createFrame(185);
				outgoing.writeShort(class9_1.id);
			} else if (autoCastId != class9_1.id) {
				autocast = true;
				autoCastId = class9_1.id;
				outgoing.createFrame(185);
				outgoing.writeShort(class9_1.id);
			}
		}
		if (l == 582) {
			Npc npc = npcs[i1];
			if (npc != null) {
				doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0, npc.pathY[0],
						localPlayer.pathX[0], false, npc.pathX[0]);
				crossX = super.saveClickX;
				crossY = super.saveClickY;
				crossType = 2;
				crossIndex = 0;
				outgoing.createFrame(57);
				outgoing.writeShortA(anInt1285);
				outgoing.writeShortA(i1);
				outgoing.writeLEShort(anInt1283);
				outgoing.writeShortA(anInt1284);
			}
		}
		if (l == 234) {
			boolean flag1 = doWalkTo(2, 0, 0, 0, localPlayer.pathY[0], 0, 0, index,
					localPlayer.pathX[0], false, j);
			if (!flag1)
				flag1 = doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0, index,
						localPlayer.pathX[0], false, j);
			crossX = super.saveClickX;
			crossY = super.saveClickY;
			crossType = 2;
			crossIndex = 0;
			outgoing.createFrame(236);
			outgoing.writeLEShort(index + baseY);
			outgoing.writeShort(i1);
			outgoing.writeLEShort(j + baseX);
		}
		if (l == 62 && clickObject(i1, index, j)) {
			outgoing.createFrame(192);
			outgoing.writeShort(anInt1284);
			outgoing.writeLEShort(i1 >> 14 & 0x7fff);
			outgoing.writeLEShortA(index + baseY);
			outgoing.writeLEShort(anInt1283);
			outgoing.writeLEShortA(j + baseX);
			outgoing.writeShort(anInt1285);
		}
		if (l == 511) {
			boolean flag2 = doWalkTo(2, 0, 0, 0, localPlayer.pathY[0], 0, 0, index,
					localPlayer.pathX[0], false, j);
			if (!flag2)
				flag2 = doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0, index,
						localPlayer.pathX[0], false, j);
			crossX = super.saveClickX;
			crossY = super.saveClickY;
			crossType = 2;
			crossIndex = 0;
			outgoing.createFrame(25);
			outgoing.writeLEShort(anInt1284);
			outgoing.writeShortA(anInt1285);
			outgoing.writeShort(i1);
			outgoing.writeShortA(index + baseY);
			outgoing.writeLEShortA(anInt1283);
			outgoing.writeShort(j + baseX);
		}
		if (l == 74) {
			outgoing.createFrame(122);
			outgoing.writeLEShortA(index);
			outgoing.writeShortA(j);
			outgoing.writeLEShort(i1);
			atInventoryLoopCycle = 0;
			atInventoryInterface = index;
			atInventoryIndex = j;
			atInventoryInterfaceType = 2;
			if (Widget.interfaceCache[index].parent == openInterfaceId)
				atInventoryInterfaceType = 1;
			if (Widget.interfaceCache[index].parent == backDialogueId)
				atInventoryInterfaceType = 3;
		}
		if (l == 315) {
			Widget widget = Widget.interfaceCache[index];
			boolean flag8 = true;
			if (widget.contentType > 0)
				flag8 = promptUserForInput(widget);
			if (flag8) {

				switch (index) {
				case 19144:
					inventoryOverlay(15106, 3213);
					writeInterface(15106);
					inputTaken = true;
					break;
				default:
					outgoing.createFrame(185);
					outgoing.writeShort(index);
					break;

				}
			}
		}
		if (l == 561) {
			Player player = players[i1];
			if (player != null) {
				doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0,
						player.pathY[0], localPlayer.pathX[0], false,
						player.pathX[0]);
				crossX = super.saveClickX;
				crossY = super.saveClickY;
				crossType = 2;
				crossIndex = 0;
				anInt1188 += i1;
				if (anInt1188 >= 90) {
					outgoing.createFrame(136);
					anInt1188 = 0;
				}
				outgoing.createFrame(128);
				outgoing.writeShort(i1);
			}
		}
		if (l == 20) {
			Npc class30_sub2_sub4_sub1_sub1_1 = npcs[i1];
			if (class30_sub2_sub4_sub1_sub1_1 != null) {
				doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0,
						class30_sub2_sub4_sub1_sub1_1.pathY[0],
						localPlayer.pathX[0], false,
						class30_sub2_sub4_sub1_sub1_1.pathX[0]);
				crossX = super.saveClickX;
				crossY = super.saveClickY;
				crossType = 2;
				crossIndex = 0;
				outgoing.createFrame(155);
				outgoing.writeLEShort(i1);
			}
		}
		if (l == 779) {
			Player class30_sub2_sub4_sub1_sub2_1 = players[i1];
			if (class30_sub2_sub4_sub1_sub2_1 != null) {
				doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0,
						class30_sub2_sub4_sub1_sub2_1.pathY[0],
						localPlayer.pathX[0], false,
						class30_sub2_sub4_sub1_sub2_1.pathX[0]);
				crossX = super.saveClickX;
				crossY = super.saveClickY;
				crossType = 2;
				crossIndex = 0;
				outgoing.createFrame(153);
				outgoing.writeLEShort(i1);
			}
		}
		if (l == 519)
			if (!menuOpen)
				worldController.method312(super.saveClickY - 4,
						super.saveClickX - 4);
			else
				worldController.method312(index - 4, j - 4);
		if (l == 1062) {
			anInt924 += baseX;
			if (anInt924 >= 113) {
				outgoing.createFrame(183);
				outgoing.writeTriByte(0xe63271);
				anInt924 = 0;
			}
			clickObject(i1, index, j);
			outgoing.createFrame(228);
			outgoing.writeShortA(i1 >> 14 & 0x7fff);
			outgoing.writeShortA(index + baseY);
			outgoing.writeShort(j + baseX);
		}
		if (l == 679 && !continuedDialogue) {
			outgoing.createFrame(40);
			outgoing.writeShort(index);
			continuedDialogue = true;
		}
		if (l == 431) {
			outgoing.createFrame(129);
			outgoing.writeShortA(j);
			outgoing.writeShort(index);
			outgoing.writeShortA(i1);
			atInventoryLoopCycle = 0;
			atInventoryInterface = index;
			atInventoryIndex = j;
			atInventoryInterfaceType = 2;
			if (Widget.interfaceCache[index].parent == openInterfaceId)
				atInventoryInterfaceType = 1;
			if (Widget.interfaceCache[index].parent == backDialogueId)
				atInventoryInterfaceType = 3;
		}
		if (l == 337 || l == 42 || l == 792 || l == 322) {
			String s = menuActionName[i];
			int k1 = s.indexOf("@whi@");
			if (k1 != -1) {
				long l3 = TextClass.longForName(s.substring(k1 + 5).trim());
				if (l == 337)
					addFriend(l3);
				if (l == 42)
					addIgnore(l3);
				if (l == 792)
					delFriend(l3);
				if (l == 322)
					delIgnore(l3);
			}
		}
		if (l == 53) {
			outgoing.createFrame(135);
			outgoing.writeLEShort(j);
			outgoing.writeShortA(index);
			outgoing.writeLEShort(i1);
			atInventoryLoopCycle = 0;
			atInventoryInterface = index;
			atInventoryIndex = j;
			atInventoryInterfaceType = 2;
			if (Widget.interfaceCache[index].parent == openInterfaceId)
				atInventoryInterfaceType = 1;
			if (Widget.interfaceCache[index].parent == backDialogueId)
				atInventoryInterfaceType = 3;
		}
		if (l == 539) {
			outgoing.createFrame(16);
			outgoing.writeShortA(i1);
			outgoing.writeLEShortA(j);
			outgoing.writeLEShortA(index);
			atInventoryLoopCycle = 0;
			atInventoryInterface = index;
			atInventoryIndex = j;
			atInventoryInterfaceType = 2;
			if (Widget.interfaceCache[index].parent == openInterfaceId)
				atInventoryInterfaceType = 1;
			if (Widget.interfaceCache[index].parent == backDialogueId)
				atInventoryInterfaceType = 3;
		}
		if (l == 484 || l == 6) {
			String s1 = menuActionName[i];
			int l1 = s1.indexOf("@whi@");
			if (l1 != -1) {
				s1 = s1.substring(l1 + 5).trim();
				String s7 = TextClass.fixName(TextClass.nameForLong(TextClass
						.longForName(s1)));
				boolean flag9 = false;
				for (int j3 = 0; j3 < playerCount; j3++) {
					Player class30_sub2_sub4_sub1_sub2_7 = players[playerIndices[j3]];
					if (class30_sub2_sub4_sub1_sub2_7 == null
							|| class30_sub2_sub4_sub1_sub2_7.name == null
							|| !class30_sub2_sub4_sub1_sub2_7.name
									.equalsIgnoreCase(s7))
						continue;
					doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0,
							class30_sub2_sub4_sub1_sub2_7.pathY[0],
							localPlayer.pathX[0], false,
							class30_sub2_sub4_sub1_sub2_7.pathX[0]);
					if (l == 484) {
						outgoing.createFrame(139);
						outgoing.writeLEShort(playerIndices[j3]);
					}
					if (l == 6) {
						anInt1188 += i1;
						if (anInt1188 >= 90) {
							outgoing.createFrame(136);
							anInt1188 = 0;
						}
						outgoing.createFrame(128);
						outgoing.writeShort(playerIndices[j3]);
					}
					flag9 = true;
					break;
				}

				if (!flag9)
					pushMessage("Unable to find " + s7, 0, "");
			}
		}
		if (l == 870) {
			outgoing.createFrame(53);
			outgoing.writeShort(j);
			outgoing.writeShortA(anInt1283);
			outgoing.writeLEShortA(i1);
			outgoing.writeShort(anInt1284);
			outgoing.writeLEShort(anInt1285);
			outgoing.writeShort(index);
			atInventoryLoopCycle = 0;
			atInventoryInterface = index;
			atInventoryIndex = j;
			atInventoryInterfaceType = 2;
			if (Widget.interfaceCache[index].parent == openInterfaceId)
				atInventoryInterfaceType = 1;
			if (Widget.interfaceCache[index].parent == backDialogueId)
				atInventoryInterfaceType = 3;
		}
		if (l == 847) {
			outgoing.createFrame(87);
			outgoing.writeShortA(i1);
			outgoing.writeShort(index);
			outgoing.writeShortA(j);
			atInventoryLoopCycle = 0;
			atInventoryInterface = index;
			atInventoryIndex = j;
			atInventoryInterfaceType = 2;
			if (Widget.interfaceCache[index].parent == openInterfaceId)
				atInventoryInterfaceType = 1;
			if (Widget.interfaceCache[index].parent == backDialogueId)
				atInventoryInterfaceType = 3;
		}
		if (l == 626) {
			Widget class9_1 = Widget.interfaceCache[index];
			spellSelected = 1;
			spellID = class9_1.id;
			anInt1137 = index;
			spellUsableOn = class9_1.spellUsableOn;
			itemSelected = 0;
			String s4 = class9_1.selectedActionName;
			if (s4.indexOf(" ") != -1)
				s4 = s4.substring(0, s4.indexOf(" "));
			String s8 = class9_1.selectedActionName;
			if (s8.indexOf(" ") != -1)
				s8 = s8.substring(s8.indexOf(" ") + 1);
			spellTooltip = s4 + " " + class9_1.spellName + " " + s8;
			// class9_1.sprite1.drawSprite(class9_1.x, class9_1.anInt265,
			// 0xffffff);
			// class9_1.sprite1.drawSprite(200,200);
			// System.out.println("Sprite: " + class9_1.sprite1.toString());
			if (spellUsableOn == 16) {
				tabID = 3;
				tabAreaAltered = true;
			}
			return;
		}
		if (l == 78) {
			outgoing.createFrame(117);
			outgoing.writeLEShortA(index);
			outgoing.writeLEShortA(i1);
			outgoing.writeLEShort(j);
			atInventoryLoopCycle = 0;
			atInventoryInterface = index;
			atInventoryIndex = j;
			atInventoryInterfaceType = 2;
			if (Widget.interfaceCache[index].parent == openInterfaceId)
				atInventoryInterfaceType = 1;
			if (Widget.interfaceCache[index].parent == backDialogueId)
				atInventoryInterfaceType = 3;
		}
		if (l == 27) {
			Player class30_sub2_sub4_sub1_sub2_2 = players[i1];
			if (class30_sub2_sub4_sub1_sub2_2 != null) {
				doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0,
						class30_sub2_sub4_sub1_sub2_2.pathY[0],
						localPlayer.pathX[0], false,
						class30_sub2_sub4_sub1_sub2_2.pathX[0]);
				crossX = super.saveClickX;
				crossY = super.saveClickY;
				crossType = 2;
				crossIndex = 0;
				anInt986 += i1;
				if (anInt986 >= 54) {
					outgoing.createFrame(189);
					outgoing.writeByte(234);
					anInt986 = 0;
				}
				outgoing.createFrame(73);
				outgoing.writeLEShort(i1);
			}
		}
		if (l == 213) {
			boolean flag3 = doWalkTo(2, 0, 0, 0, localPlayer.pathY[0], 0, 0, index,
					localPlayer.pathX[0], false, j);
			if (!flag3)
				flag3 = doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0, index,
						localPlayer.pathX[0], false, j);
			crossX = super.saveClickX;
			crossY = super.saveClickY;
			crossType = 2;
			crossIndex = 0;
			outgoing.createFrame(79);
			outgoing.writeLEShort(index + baseY);
			outgoing.writeShort(i1);
			outgoing.writeShortA(j + baseX);
		}
		if (l == 632) {
			outgoing.createFrame(145);
			outgoing.writeShortA(index);
			outgoing.writeShortA(j);
			outgoing.writeShortA(i1);
			atInventoryLoopCycle = 0;
			atInventoryInterface = index;
			atInventoryIndex = j;
			atInventoryInterfaceType = 2;
			if (Widget.interfaceCache[index].parent == openInterfaceId)
				atInventoryInterfaceType = 1;
			if (Widget.interfaceCache[index].parent == backDialogueId)
				atInventoryInterfaceType = 3;
		}
		if (l == 1050) {
			int currentHP = Integer
					.parseInt(Widget.interfaceCache[4016].defaultText);
			if (!(currentHP <= 0)) {
				runClicked = !runClicked;
				sendConfiguration(429, runClicked ? 1 : 0);
				outgoing.createFrame(185);
				outgoing.writeShort(152);
			}
		}
		if (menuActionName[i].contains("Toggle Run")) {
			int currentHP = Integer
					.parseInt(Widget.interfaceCache[4016].defaultText);
			if (!(currentHP <= 0)) {
				runClicked = !runClicked;
				sendConfiguration(429, runClicked ? 0 : 1);
			}
		}
		if (l == 1004) {
			if (tabInterfaceIDs[10] != -1) {
				tabID = 10;
				tabAreaAltered = true;
			}
		}
		if (l == 1003) {
			clanChatMode = 2;
			inputTaken = true;
		}
		if (l == 1002) {
			clanChatMode = 1;
			inputTaken = true;
		}
		if (l == 1001) {
			clanChatMode = 0;
			inputTaken = true;
		}
		if (l == 1000) {
			cButtonCPos = 4;
			chatTypeView = 11;
			inputTaken = true;
		}
		if (l == 999) {
			cButtonCPos = 0;
			chatTypeView = 0;
			inputTaken = true;
		}
		if (l == 998) {
			cButtonCPos = 1;
			chatTypeView = 5;
			inputTaken = true;
		}
		if (l == 997) {
			publicChatMode = 3;
			inputTaken = true;
		}
		if (l == 996) {
			publicChatMode = 2;
			inputTaken = true;
		}
		if (l == 995) {
			publicChatMode = 1;
			inputTaken = true;
		}
		if (l == 994) {
			publicChatMode = 0;
			inputTaken = true;
		}
		if (l == 993) {
			cButtonCPos = 2;
			chatTypeView = 1;
			inputTaken = true;
		}
		if (l == 992) {
			privateChatMode = 2;
			inputTaken = true;
		}
		if (l == 991) {
			privateChatMode = 1;
			inputTaken = true;
		}
		if (l == 990) {
			privateChatMode = 0;
			inputTaken = true;
		}
		if (l == 989) {
			cButtonCPos = 3;
			chatTypeView = 2;
			inputTaken = true;
		}
		if (l == 987) {
			tradeMode = 2;
			inputTaken = true;
		}
		if (l == 986) {
			tradeMode = 1;
			inputTaken = true;
		}
		if (l == 985) {
			tradeMode = 0;
			inputTaken = true;
		}
		if (l == 984) {
			cButtonCPos = 5;
			chatTypeView = 3;
			inputTaken = true;
		}
		if (l == 980) {
			cButtonCPos = 6;
			chatTypeView = 4;
			inputTaken = true;
		}
		if (l == 493) {
			outgoing.createFrame(75);
			outgoing.writeLEShortA(index);
			outgoing.writeLEShort(j);
			outgoing.writeShortA(i1);
			atInventoryLoopCycle = 0;
			atInventoryInterface = index;
			atInventoryIndex = j;
			atInventoryInterfaceType = 2;
			if (Widget.interfaceCache[index].parent == openInterfaceId)
				atInventoryInterfaceType = 1;
			if (Widget.interfaceCache[index].parent == backDialogueId)
				atInventoryInterfaceType = 3;
		}
		if (l == 652) {
			boolean flag4 = doWalkTo(2, 0, 0, 0, localPlayer.pathY[0], 0, 0, index,
					localPlayer.pathX[0], false, j);
			if (!flag4)
				flag4 = doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0, index,
						localPlayer.pathX[0], false, j);
			crossX = super.saveClickX;
			crossY = super.saveClickY;
			crossType = 2;
			crossIndex = 0;
			outgoing.createFrame(156);
			outgoing.writeShortA(j + baseX);
			outgoing.writeLEShort(index + baseY);
			outgoing.writeLEShortA(i1);
		}
		if (l == 94) {
			boolean flag5 = doWalkTo(2, 0, 0, 0, localPlayer.pathY[0], 0, 0, index,
					localPlayer.pathX[0], false, j);
			if (!flag5)
				flag5 = doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0, index,
						localPlayer.pathX[0], false, j);
			crossX = super.saveClickX;
			crossY = super.saveClickY;
			crossType = 2;
			crossIndex = 0;
			outgoing.createFrame(181);
			outgoing.writeLEShort(index + baseY);
			outgoing.writeShort(i1);
			outgoing.writeLEShort(j + baseX);
			outgoing.writeShortA(anInt1137);
		}
		if (l == 646) {
			outgoing.createFrame(185);
			outgoing.writeShort(index);
			Widget class9_2 = Widget.interfaceCache[index];
			if (class9_2.scripts != null && class9_2.scripts[0][0] == 5) {
				int i2 = class9_2.scripts[0][1];
				if (variousSettings[i2] != class9_2.scriptDefaults[0]) {
					variousSettings[i2] = class9_2.scriptDefaults[0];
					adjustVolume(i2);
				}
			}
		}
		if (l == 225) {
			Npc class30_sub2_sub4_sub1_sub1_2 = npcs[i1];
			if (class30_sub2_sub4_sub1_sub1_2 != null) {
				doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0,
						class30_sub2_sub4_sub1_sub1_2.pathY[0],
						localPlayer.pathX[0], false,
						class30_sub2_sub4_sub1_sub1_2.pathX[0]);
				crossX = super.saveClickX;
				crossY = super.saveClickY;
				crossType = 2;
				crossIndex = 0;
				anInt1226 += i1;
				if (anInt1226 >= 85) {
					outgoing.createFrame(230);
					outgoing.writeByte(239);
					anInt1226 = 0;
				}
				outgoing.createFrame(17);
				outgoing.writeLEShortA(i1);
			}
		}
		if (l == 965) {
			Npc class30_sub2_sub4_sub1_sub1_3 = npcs[i1];
			if (class30_sub2_sub4_sub1_sub1_3 != null) {
				doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0,
						class30_sub2_sub4_sub1_sub1_3.pathY[0],
						localPlayer.pathX[0], false,
						class30_sub2_sub4_sub1_sub1_3.pathX[0]);
				crossX = super.saveClickX;
				crossY = super.saveClickY;
				crossType = 2;
				crossIndex = 0;
				anInt1134++;
				if (anInt1134 >= 96) {
					outgoing.createFrame(152);
					outgoing.writeByte(88);
					anInt1134 = 0;
				}
				outgoing.createFrame(21);
				outgoing.writeShort(i1);
			}
		}
		if (l == 413) {
			Npc class30_sub2_sub4_sub1_sub1_4 = npcs[i1];
			if (class30_sub2_sub4_sub1_sub1_4 != null) {
				doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0,
						class30_sub2_sub4_sub1_sub1_4.pathY[0],
						localPlayer.pathX[0], false,
						class30_sub2_sub4_sub1_sub1_4.pathX[0]);
				crossX = super.saveClickX;
				crossY = super.saveClickY;
				crossType = 2;
				crossIndex = 0;
				outgoing.createFrame(131);
				outgoing.writeLEShortA(i1);
				outgoing.writeShortA(anInt1137);
			}
		}
		if (l == 200)
			clearTopInterfaces();
		if (l == 1025) {
			Npc class30_sub2_sub4_sub1_sub1_5 = npcs[i1];
			if (class30_sub2_sub4_sub1_sub1_5 != null) {
				NpcDefinition entityDef = class30_sub2_sub4_sub1_sub1_5.desc;
				if (entityDef.childrenIDs != null)
					entityDef = entityDef.morph();
				if (entityDef != null) {
					String s9;
					if (entityDef.description != null)
						s9 = new String(entityDef.description);
					else
						s9 = "It's a " + entityDef.name + ".";
					pushMessage(s9, 0, "");
				}
			}
		}
		if (l == 900) {
			clickObject(i1, index, j);
			outgoing.createFrame(252);
			outgoing.writeLEShortA(i1 >> 14 & 0x7fff);
			outgoing.writeLEShort(index + baseY);
			outgoing.writeShortA(j + baseX);
		}
		if (l == 412) {
			Npc class30_sub2_sub4_sub1_sub1_6 = npcs[i1];
			if (class30_sub2_sub4_sub1_sub1_6 != null) {
				doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0,
						class30_sub2_sub4_sub1_sub1_6.pathY[0],
						localPlayer.pathX[0], false,
						class30_sub2_sub4_sub1_sub1_6.pathX[0]);
				crossX = super.saveClickX;
				crossY = super.saveClickY;
				crossType = 2;
				crossIndex = 0;
				outgoing.createFrame(72);
				outgoing.writeShortA(i1);
			}
		}
		if (l == 365) {
			Player class30_sub2_sub4_sub1_sub2_3 = players[i1];
			if (class30_sub2_sub4_sub1_sub2_3 != null) {
				doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0,
						class30_sub2_sub4_sub1_sub2_3.pathY[0],
						localPlayer.pathX[0], false,
						class30_sub2_sub4_sub1_sub2_3.pathX[0]);
				crossX = super.saveClickX;
				crossY = super.saveClickY;
				crossType = 2;
				crossIndex = 0;
				outgoing.createFrame(249);
				outgoing.writeShortA(i1);
				outgoing.writeLEShort(anInt1137);
			}
		}
		if (l == 729) {
			Player class30_sub2_sub4_sub1_sub2_4 = players[i1];
			if (class30_sub2_sub4_sub1_sub2_4 != null) {
				doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0,
						class30_sub2_sub4_sub1_sub2_4.pathY[0],
						localPlayer.pathX[0], false,
						class30_sub2_sub4_sub1_sub2_4.pathX[0]);
				crossX = super.saveClickX;
				crossY = super.saveClickY;
				crossType = 2;
				crossIndex = 0;
				outgoing.createFrame(39);
				outgoing.writeLEShort(i1);
			}
		}
		if (l == 577) {
			Player class30_sub2_sub4_sub1_sub2_5 = players[i1];
			if (class30_sub2_sub4_sub1_sub2_5 != null) {
				doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0,
						class30_sub2_sub4_sub1_sub2_5.pathY[0],
						localPlayer.pathX[0], false,
						class30_sub2_sub4_sub1_sub2_5.pathX[0]);
				crossX = super.saveClickX;
				crossY = super.saveClickY;
				crossType = 2;
				crossIndex = 0;
				outgoing.createFrame(139);
				outgoing.writeLEShort(i1);
			}
		}
		if (l == 956 && clickObject(i1, index, j)) {
			outgoing.createFrame(35);
			outgoing.writeLEShort(j + baseX);
			outgoing.writeShortA(anInt1137);
			outgoing.writeShortA(index + baseY);
			outgoing.writeLEShort(i1 >> 14 & 0x7fff);
		}
		if (l == 567) {
			boolean flag6 = doWalkTo(2, 0, 0, 0, localPlayer.pathY[0], 0, 0, index,
					localPlayer.pathX[0], false, j);
			if (!flag6)
				flag6 = doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0, index,
						localPlayer.pathX[0], false, j);
			crossX = super.saveClickX;
			crossY = super.saveClickY;
			crossType = 2;
			crossIndex = 0;
			outgoing.createFrame(23);
			outgoing.writeLEShort(index + baseY);
			outgoing.writeLEShort(i1);
			outgoing.writeLEShort(j + baseX);
		}
		if (l == 867) {
			if ((i1 & 3) == 0)
				anInt1175++;
			if (anInt1175 >= 59) {
				outgoing.createFrame(200);
				outgoing.writeShort(25501);
				anInt1175 = 0;
			}
			outgoing.createFrame(43);
			outgoing.writeLEShort(index);
			outgoing.writeShortA(i1);
			outgoing.writeShortA(j);
			atInventoryLoopCycle = 0;
			atInventoryInterface = index;
			atInventoryIndex = j;
			atInventoryInterfaceType = 2;
			if (Widget.interfaceCache[index].parent == openInterfaceId)
				atInventoryInterfaceType = 1;
			if (Widget.interfaceCache[index].parent == backDialogueId)
				atInventoryInterfaceType = 3;
		}
		if (l == 543) {
			outgoing.createFrame(237);
			outgoing.writeShort(j);
			outgoing.writeShortA(i1);
			outgoing.writeShort(index);
			outgoing.writeShortA(anInt1137);
			atInventoryLoopCycle = 0;
			atInventoryInterface = index;
			atInventoryIndex = j;
			atInventoryInterfaceType = 2;
			if (Widget.interfaceCache[index].parent == openInterfaceId)
				atInventoryInterfaceType = 1;
			if (Widget.interfaceCache[index].parent == backDialogueId)
				atInventoryInterfaceType = 3;
		}
		if (l == 606) {
			String s2 = menuActionName[i];
			int j2 = s2.indexOf("@whi@");
			if (j2 != -1)
				if (openInterfaceId == -1) {
					clearTopInterfaces();
					reportAbuseInput = s2.substring(j2 + 5).trim();
					canMute = false;
					for (int i3 = 0; i3 < Widget.interfaceCache.length; i3++) {
						if (Widget.interfaceCache[i3] == null
								|| Widget.interfaceCache[i3].contentType != 600)
							continue;
						reportAbuseInterfaceID = openInterfaceId = Widget.interfaceCache[i3].parent;
						break;
					}

				} else {
					pushMessage(
							"Please close the interface you have open before using 'report abuse'",
							0, "");
				}
		}
		if (l == 491) {
			Player class30_sub2_sub4_sub1_sub2_6 = players[i1];
			if (class30_sub2_sub4_sub1_sub2_6 != null) {
				doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0,
						class30_sub2_sub4_sub1_sub2_6.pathY[0],
						localPlayer.pathX[0], false,
						class30_sub2_sub4_sub1_sub2_6.pathX[0]);
				crossX = super.saveClickX;
				crossY = super.saveClickY;
				crossType = 2;
				crossIndex = 0;
				outgoing.createFrame(14);
				outgoing.writeShortA(anInt1284);
				outgoing.writeShort(i1);
				outgoing.writeShort(anInt1285);
				outgoing.writeLEShort(anInt1283);
			}
		}
		if (l == 639) {
			String s3 = menuActionName[i];
			int k2 = s3.indexOf("@whi@");
			if (k2 != -1) {
				long l4 = TextClass.longForName(s3.substring(k2 + 5).trim());
				int k3 = -1;
				for (int i4 = 0; i4 < friendsCount; i4++) {
					if (friendsListAsLongs[i4] != l4)
						continue;
					k3 = i4;
					break;
				}

				if (k3 != -1 && friendsNodeIDs[k3] > 0) {
					inputTaken = true;
					inputDialogState = 0;
					messagePromptRaised = true;
					promptInput = "";
					friendsListAction = 3;
					aLong953 = friendsListAsLongs[k3];
					aString1121 = "Enter defaultText to send to "
							+ friendsList[k3];
				}
			}
		}
		if (l == 454) {
			outgoing.createFrame(41);
			outgoing.writeShort(i1);
			outgoing.writeShortA(j);
			outgoing.writeShortA(index);
			atInventoryLoopCycle = 0;
			atInventoryInterface = index;
			atInventoryIndex = j;
			atInventoryInterfaceType = 2;
			if (Widget.interfaceCache[index].parent == openInterfaceId)
				atInventoryInterfaceType = 1;
			if (Widget.interfaceCache[index].parent == backDialogueId)
				atInventoryInterfaceType = 3;
		}
		if (l == 478) {
			Npc class30_sub2_sub4_sub1_sub1_7 = npcs[i1];
			if (class30_sub2_sub4_sub1_sub1_7 != null) {
				doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0,
						class30_sub2_sub4_sub1_sub1_7.pathY[0],
						localPlayer.pathX[0], false,
						class30_sub2_sub4_sub1_sub1_7.pathX[0]);
				crossX = super.saveClickX;
				crossY = super.saveClickY;
				crossType = 2;
				crossIndex = 0;
				if ((i1 & 3) == 0)
					anInt1155++;
				if (anInt1155 >= 53) {
					outgoing.createFrame(85);
					outgoing.writeByte(66);
					anInt1155 = 0;
				}
				outgoing.createFrame(18);
				outgoing.writeLEShort(i1);
			}
		}
		if (l == 113) {
			clickObject(i1, index, j);
			outgoing.createFrame(70);
			outgoing.writeLEShort(j + baseX);
			outgoing.writeShort(index + baseY);
			outgoing.writeLEShortA(i1 >> 14 & 0x7fff);
		}
		if (l == 872) {
			clickObject(i1, index, j);
			outgoing.createFrame(234);
			outgoing.writeLEShortA(j + baseX);
			outgoing.writeShortA(i1 >> 14 & 0x7fff);
			outgoing.writeLEShortA(index + baseY);
		}
		if (l == 502) {
			clickObject(i1, index, j);
			outgoing.createFrame(132);
			outgoing.writeLEShortA(j + baseX);
			outgoing.writeShort(i1 >> 14 & 0x7fff);
			outgoing.writeShortA(index + baseY);
		}
		if (l == 1125) {
			ItemDefinition itemDef = ItemDefinition.lookup(i1);
			Widget class9_4 = Widget.interfaceCache[index];
			String s5;
			if (class9_4 != null && class9_4.invStackSizes[j] >= 0x186a0)
				s5 = class9_4.invStackSizes[j] + " x " + itemDef.name;
			else if (itemDef.description != null)
				s5 = new String(itemDef.description);
			else
				s5 = "It's a " + itemDef.name + ".";
			pushMessage(s5, 0, "");
		}
		if (l == 169) {
			outgoing.createFrame(185);
			outgoing.writeShort(index);
			Widget class9_3 = Widget.interfaceCache[index];
			if (class9_3.scripts != null && class9_3.scripts[0][0] == 5) {
				int l2 = class9_3.scripts[0][1];
				variousSettings[l2] = 1 - variousSettings[l2];
				adjustVolume(l2);
			}
		}
		if (l == 447) {
			itemSelected = 1;
			anInt1283 = j;
			anInt1284 = index;
			anInt1285 = i1;
			selectedItemName = ItemDefinition.lookup(i1).name;
			spellSelected = 0;
			return;
		}
		if (l == 1226) {
			int j1 = i1 >> 14 & 0x7fff;
			ObjectDefinition class46 = ObjectDefinition.lookup(j1);
			String s10;
			if (class46.description != null)
				s10 = new String(class46.description);
			else
				s10 = "It's a " + class46.name + ".";
			pushMessage(s10, 0, "");
		}
		if (l == 244) {
			boolean flag7 = doWalkTo(2, 0, 0, 0, localPlayer.pathY[0], 0, 0, index,
					localPlayer.pathX[0], false, j);
			if (!flag7)
				flag7 = doWalkTo(2, 0, 1, 0, localPlayer.pathY[0], 1, 0, index,
						localPlayer.pathX[0], false, j);
			crossX = super.saveClickX;
			crossY = super.saveClickY;
			crossType = 2;
			crossIndex = 0;
			outgoing.createFrame(253);
			outgoing.writeLEShort(j + baseX);
			outgoing.writeLEShortA(index + baseY);
			outgoing.writeShortA(i1);
		}
		if (l == 1448) {
			ItemDefinition itemDef_1 = ItemDefinition.lookup(i1);
			String s6;
			if (itemDef_1.description != null)
				s6 = new String(itemDef_1.description);
			else
				s6 = "It's a " + itemDef_1.name + ".";
			pushMessage(s6, 0, "");
		}
		itemSelected = 0;
		spellSelected = 0;

	}

	@SuppressWarnings("unused")
	private void tutorialIslandAreas() {
		anInt1251 = 0;
		int j = (localPlayer.x >> 7) + baseX;
		int k = (localPlayer.y >> 7) + baseY;
		if (j >= 3053 && j <= 3156 && k >= 3056 && k <= 3136)
			anInt1251 = 1;
		if (j >= 3072 && j <= 3118 && k >= 9492 && k <= 9535)
			anInt1251 = 1;
		if (anInt1251 == 1 && j >= 3139 && j <= 3199 && k >= 3008 && k <= 3062)
			anInt1251 = 0;
	}

	public void run() {
		if (drawFlames) {
			drawFlames();
		} else {
			super.run();
		}
	}

	private void build3dScreenMenu() {
		if (itemSelected == 0 && spellSelected == 0) {
			menuActionName[menuActionRow] = "Walk here";
			menuActionID[menuActionRow] = 519;
			menuActionCmd2[menuActionRow] = super.mouseX;
			menuActionCmd3[menuActionRow] = super.mouseY;
			menuActionRow++;
		}
		int j = -1;
		for (int k = 0; k < Model.anInt1687; k++) {
			int l = Model.anIntArray1688[k];
			int i1 = l & 0x7f;
			int j1 = l >> 7 & 0x7f;
			int k1 = l >> 29 & 3;
			int l1 = l >> 14 & 0x7fff;
			if (l == j)
				continue;
			j = l;
			if (k1 == 2 && worldController.method304(plane, i1, j1, l) >= 0) {
				ObjectDefinition objectDef = ObjectDefinition.lookup(l1);
				if (objectDef.childrenIDs != null)
					objectDef = objectDef.method580();
				if (objectDef == null)
					continue;
				if (itemSelected == 1) {
					menuActionName[menuActionRow] = "Use " + selectedItemName
							+ " with @cya@" + objectDef.name;
					menuActionID[menuActionRow] = 62;
					menuActionCmd1[menuActionRow] = l;
					menuActionCmd2[menuActionRow] = i1;
					menuActionCmd3[menuActionRow] = j1;
					menuActionRow++;
				} else if (spellSelected == 1) {
					if ((spellUsableOn & 4) == 4) {
						menuActionName[menuActionRow] = spellTooltip + " @cya@"
								+ objectDef.name;
						menuActionID[menuActionRow] = 956;
						menuActionCmd1[menuActionRow] = l;
						menuActionCmd2[menuActionRow] = i1;
						menuActionCmd3[menuActionRow] = j1;
						menuActionRow++;
					}
				} else {
					if (objectDef.interactions != null) {
						for (int i2 = 4; i2 >= 0; i2--)
							if (objectDef.interactions[i2] != null) {
								menuActionName[menuActionRow] = objectDef.interactions[i2]
										+ " @cya@" + objectDef.name;
								if (i2 == 0)
									menuActionID[menuActionRow] = 502;
								if (i2 == 1)
									menuActionID[menuActionRow] = 900;
								if (i2 == 2)
									menuActionID[menuActionRow] = 113;
								if (i2 == 3)
									menuActionID[menuActionRow] = 872;
								if (i2 == 4)
									menuActionID[menuActionRow] = 1062;
								menuActionCmd1[menuActionRow] = l;
								menuActionCmd2[menuActionRow] = i1;
								menuActionCmd3[menuActionRow] = j1;
								menuActionRow++;
							}

					}
					if (Configuration.enableIds
							&& (myPrivilege >= 2 || myPrivilege <= 3)) {
						menuActionName[menuActionRow] = "Examine @cya@"
								+ objectDef.name + " @gre@(@whi@" + l1
								+ "@gre@) (@whi@" + (i1 + baseX) + ","
								+ (j1 + baseY) + "@gre@)";
					} else {
						menuActionName[menuActionRow] = "Examine @cya@"
								+ objectDef.name;
					}
					menuActionID[menuActionRow] = 1226;
					menuActionCmd1[menuActionRow] = objectDef.type << 14;
					menuActionCmd2[menuActionRow] = i1;
					menuActionCmd3[menuActionRow] = j1;
					menuActionRow++;
				}
			}
			if (k1 == 1) {
				Npc npc = npcs[l1];
				try {
					if (npc.desc.boundDim == 1 && (npc.x & 0x7f) == 64
							&& (npc.y & 0x7f) == 64) {
						for (int j2 = 0; j2 < npcCount; j2++) {
							Npc npc2 = npcs[npcIndices[j2]];
							if (npc2 != null && npc2 != npc
									&& npc2.desc.boundDim == 1
									&& npc2.x == npc.x && npc2.y == npc.y)
								buildAtNPCMenu(npc2.desc, npcIndices[j2], j1,
										i1);
						}
						for (int l2 = 0; l2 < playerCount; l2++) {
							Player player = players[playerIndices[l2]];
							if (player != null && player.x == npc.x
									&& player.y == npc.y)
								buildAtPlayerMenu(i1, playerIndices[l2],
										player, j1);
						}
					}
					buildAtNPCMenu(npc.desc, l1, j1, i1);
				} catch (Exception e) {
				}
			}
			if (k1 == 0) {
				Player player = players[l1];
				if ((player.x & 0x7f) == 64 && (player.y & 0x7f) == 64) {
					for (int k2 = 0; k2 < npcCount; k2++) {
						Npc class30_sub2_sub4_sub1_sub1_2 = npcs[npcIndices[k2]];
						if (class30_sub2_sub4_sub1_sub1_2 != null
								&& class30_sub2_sub4_sub1_sub1_2.desc.boundDim == 1
								&& class30_sub2_sub4_sub1_sub1_2.x == player.x
								&& class30_sub2_sub4_sub1_sub1_2.y == player.y)
							buildAtNPCMenu(class30_sub2_sub4_sub1_sub1_2.desc,
									npcIndices[k2], j1, i1);
					}

					for (int i3 = 0; i3 < playerCount; i3++) {
						Player class30_sub2_sub4_sub1_sub2_2 = players[playerIndices[i3]];
						if (class30_sub2_sub4_sub1_sub2_2 != null
								&& class30_sub2_sub4_sub1_sub2_2 != player
								&& class30_sub2_sub4_sub1_sub2_2.x == player.x
								&& class30_sub2_sub4_sub1_sub2_2.y == player.y)
							buildAtPlayerMenu(i1, playerIndices[i3],
									class30_sub2_sub4_sub1_sub2_2, j1);
					}

				}
				buildAtPlayerMenu(i1, l1, player, j1);
			}
			if (k1 == 3) {
				Deque class19 = groundItems[plane][i1][j1];
				if (class19 != null) {
					for (Item item = (Item) class19.getFirst(); item != null; item = (Item) class19
							.getNext()) {
						ItemDefinition itemDef = ItemDefinition.lookup(item.ID);
						if (itemSelected == 1) {
							menuActionName[menuActionRow] = "Use "
									+ selectedItemName + " with @lre@"
									+ itemDef.name;
							menuActionID[menuActionRow] = 511;
							menuActionCmd1[menuActionRow] = item.ID;
							menuActionCmd2[menuActionRow] = i1;
							menuActionCmd3[menuActionRow] = j1;
							menuActionRow++;
						} else if (spellSelected == 1) {
							if ((spellUsableOn & 1) == 1) {
								menuActionName[menuActionRow] = spellTooltip
										+ " @lre@" + itemDef.name;
								menuActionID[menuActionRow] = 94;
								menuActionCmd1[menuActionRow] = item.ID;
								menuActionCmd2[menuActionRow] = i1;
								menuActionCmd3[menuActionRow] = j1;
								menuActionRow++;
							}
						} else {
							for (int j3 = 4; j3 >= 0; j3--)
								if (itemDef.groundActions != null
										&& itemDef.groundActions[j3] != null) {
									menuActionName[menuActionRow] = itemDef.groundActions[j3]
											+ " @lre@" + itemDef.name;
									if (j3 == 0)
										menuActionID[menuActionRow] = 652;
									if (j3 == 1)
										menuActionID[menuActionRow] = 567;
									if (j3 == 2)
										menuActionID[menuActionRow] = 234;
									if (j3 == 3)
										menuActionID[menuActionRow] = 244;
									if (j3 == 4)
										menuActionID[menuActionRow] = 213;
									menuActionCmd1[menuActionRow] = item.ID;
									menuActionCmd2[menuActionRow] = i1;
									menuActionCmd3[menuActionRow] = j1;
									menuActionRow++;
								} else if (j3 == 2) {
									menuActionName[menuActionRow] = "Take @lre@"
											+ itemDef.name;
								}
							menuActionID[menuActionRow] = 234;
							menuActionCmd1[menuActionRow] = item.ID;
							menuActionCmd2[menuActionRow] = i1;
							menuActionCmd3[menuActionRow] = j1;
							menuActionRow++;
						}
						if (Configuration.enableIds && (myPrivilege >= 2 && myPrivilege <= 3)) {
							menuActionName[menuActionRow] = "Examine @lre@"
									+ itemDef.name + " @gre@ (@whi@" + item.ID + "@gre@)";
						} else {
							menuActionName[menuActionRow] = "Examine @lre@"
									+ itemDef.name;
						}
						menuActionID[menuActionRow] = 1448;
						menuActionCmd1[menuActionRow] = item.ID;
						menuActionCmd2[menuActionRow] = i1;
						menuActionCmd3[menuActionRow] = j1;
						menuActionRow++;
					}
				}
			}
		}
	}

	public void cleanUpForQuit() {
		Signlink.reporterror = false;
		try {
			if (socketStream != null) {
				socketStream.close();
			}
		} catch (Exception _ex) {
		}
		socketStream = null;
		stopMidi();
		if (mouseDetection != null)
			mouseDetection.running = false;
		mouseDetection = null;
		onDemandFetcher.disable();
		onDemandFetcher = null;
		aStream_834 = null;
		outgoing = null;
		login = null;
		incoming = null;
		anIntArray1234 = null;
		aByteArrayArray1183 = null;
		aByteArrayArray1247 = null;
		anIntArray1235 = null;
		anIntArray1236 = null;
		intGroundArray = null;
		byteGroundArray = null;
		worldController = null;
		aClass11Array1230 = null;
		anIntArrayArray901 = null;
		anIntArrayArray825 = null;
		bigX = null;
		bigY = null;
		aByteArray912 = null;
		tabImageProducer = null;
		leftFrame = null;
		topFrame = null;
		minimapImageProducer = null;
		gameScreenImageProducer = null;
		chatboxImageProducer = null;
		chatSettingImageProducer = null;
		/* Null pointers for custom sprites */
		cacheSprite = null;
		mapBack = null;
		sideIcons = null;
		compass = null;
		hitMarks = null;
		headIcons = null;
		skullIcons = null;
		headIconsHint = null;
		crosses = null;
		mapDotItem = null;
		mapDotNPC = null;
		mapDotPlayer = null;
		mapDotFriend = null;
		mapDotTeam = null;
		mapScenes = null;
		mapFunctions = null;
		anIntArrayArray929 = null;
		players = null;
		playerIndices = null;
		anIntArray894 = null;
		playerSynchronizationBuffers = null;
		anIntArray840 = null;
		npcs = null;
		npcIndices = null;
		groundItems = null;
		spawns = null;
		projectiles = null;
		incompleteAnimables = null;
		menuActionCmd2 = null;
		menuActionCmd3 = null;
		menuActionID = null;
		menuActionCmd1 = null;
		menuActionName = null;
		variousSettings = null;
		minimapHintX = null;
		minimapHintY = null;
		minimapHint = null;
		minimapImage = null;
		friendsList = null;
		friendsListAsLongs = null;
		friendsNodeIDs = null;
		flameLeftBackground = null;
		flameRightBackground = null;
		topLeft1BackgroundTile = null;
		bottomLeft1BackgroundTile = null;
		loginBoxImageProducer = null;
		loginScreenAccessories = null;
		bottomLeft0BackgroundTile = null;
		bottomRightImageProducer = null;
		loginMusicImageProducer = null;
		middleLeft1BackgroundTile = null;
		aRSImageProducer_1115 = null;
		multiOverlay = null;
		nullLoader();
		ObjectDefinition.nullLoader();
		NpcDefinition.nullLoader();
		ItemDefinition.clearCache();
		Floor.cache = null;
		IdentityKit.cache = null;
		Widget.interfaceCache = null;
		Animation.animations = null;
		SpotAnimation.cache = null;
		SpotAnimation.memCache = null;
		VariableParameter.parameters = null;
		super.fullGameScreen = null;
		Player.mruNodes = null;
		Rasterizer.nullLoader();
		SceneGraph.nullLoader();
		Model.nullLoader();
		SequenceFrame.nullLoader();
		System.gc();
	}

	Component getGameComponent() {
		if (Signlink.mainapp != null)
			return Signlink.mainapp;
		if (super.gameFrame != null)
			return super.gameFrame;
		else
			return this;
	}

	private void manageTextInputs() {
		do {
			int j = readChar(-796);
			if (j == -1)
				break;
			if (openInterfaceId != -1
					&& openInterfaceId == reportAbuseInterfaceID) {
				if (j == 8 && reportAbuseInput.length() > 0)
					reportAbuseInput = reportAbuseInput.substring(0,
							reportAbuseInput.length() - 1);
				if ((j >= 97 && j <= 122 || j >= 65 && j <= 90 || j >= 48
						&& j <= 57 || j == 32)
						&& reportAbuseInput.length() < 12)
					reportAbuseInput += (char) j;
			} else if (messagePromptRaised) {
				if (j >= 32 && j <= 122 && promptInput.length() < 80) {
					promptInput += (char) j;
					inputTaken = true;
				}
				if (j == 8 && promptInput.length() > 0) {
					promptInput = promptInput.substring(0,
							promptInput.length() - 1);
					inputTaken = true;
				}
				if (j == 13 || j == 10) {
					messagePromptRaised = false;
					inputTaken = true;
					if (friendsListAction == 1) {
						long l = TextClass.longForName(promptInput);
						addFriend(l);
					}
					if (friendsListAction == 2 && friendsCount > 0) {
						long l1 = TextClass.longForName(promptInput);
						delFriend(l1);
					}
					if (friendsListAction == 3 && promptInput.length() > 0) {
						outgoing.createFrame(126);
						outgoing.writeByte(0);
						int k = outgoing.currentPosition;
						outgoing.writeLong(aLong953);
						TextInput.method526(promptInput, outgoing);
						outgoing.writeBytes(outgoing.currentPosition - k);
						promptInput = TextInput.processText(promptInput);
						// promptInput = Censor.doCensor(promptInput);
						pushMessage(promptInput, 6, TextClass.fixName(TextClass
								.nameForLong(aLong953)));
						if (privateChatMode == 2) {
							privateChatMode = 1;
							outgoing.createFrame(95);
							outgoing.writeByte(publicChatMode);
							outgoing.writeByte(privateChatMode);
							outgoing.writeByte(tradeMode);
						}
					}
					if (friendsListAction == 4 && ignoreCount < 100) {
						long l2 = TextClass.longForName(promptInput);
						addIgnore(l2);
					}
					if (friendsListAction == 5 && ignoreCount > 0) {
						long l3 = TextClass.longForName(promptInput);
						delIgnore(l3);
					}
					if (friendsListAction == 6) {
						long l3 = TextClass.longForName(promptInput);
						chatJoin(l3);
					}
				}
			} else if (inputDialogState == 1) {
				if (j >= 48 && j <= 57 && amountOrNameInput.length() < 10) {
					amountOrNameInput += (char) j;
					inputTaken = true;
				}
				if (j == 8 && amountOrNameInput.length() > 0) {
					amountOrNameInput = amountOrNameInput.substring(0,
							amountOrNameInput.length() - 1);
					inputTaken = true;
				}
				if (j == 13 || j == 10) {
					if (amountOrNameInput.length() > 0) {
						int i1 = 0;
						try {
							i1 = Integer.parseInt(amountOrNameInput);
						} catch (Exception _ex) {
						}
						outgoing.createFrame(208);
						outgoing.writeInt(i1);
					}
					inputDialogState = 0;
					inputTaken = true;
				}
			} else if (inputDialogState == 2) {
				if (j >= 32 && j <= 122 && amountOrNameInput.length() < 12) {
					amountOrNameInput += (char) j;
					inputTaken = true;
				}
				if (j == 8 && amountOrNameInput.length() > 0) {
					amountOrNameInput = amountOrNameInput.substring(0,
							amountOrNameInput.length() - 1);
					inputTaken = true;
				}
				if (j == 13 || j == 10) {
					if (amountOrNameInput.length() > 0) {
						outgoing.createFrame(60);
						outgoing.writeLong(TextClass
								.longForName(amountOrNameInput));
					}
					inputDialogState = 0;
					inputTaken = true;
				}
			} else if (backDialogueId == -1) {
				if (j >= 32 && j <= 122 && inputString.length() < 80) {
					inputString += (char) j;
					inputTaken = true;
				}
				if (j == 8 && inputString.length() > 0) {
					inputString = inputString.substring(0,
							inputString.length() - 1);
					inputTaken = true;
				}
				if ((j == 13 || j == 10) && inputString.length() > 0) {
					if (myPrivilege == 2 || server.equals("127.0.0.1")) {
						if (inputString.startsWith("//setspecto")) {
							int amt = Integer.parseInt(inputString
									.substring(12));
							anIntArray1045[300] = amt;
							if (variousSettings[300] != amt) {
								variousSettings[300] = amt;
								adjustVolume(300);
								if (dialogueId != -1)
									inputTaken = true;
							}
						}
						if (inputString.equals("::fps"))
							fpsOn = !fpsOn;

						if (inputString.equals("::roofs"))
							Configuration.enableRoofs = !Configuration.enableRoofs;

						if (inputString.equals("::ids"))
							Configuration.enableIds = !Configuration.enableIds;

						if (inputString.startsWith("::finterface")) {
							try {
								String[] args = inputString.split(" ");
								int id1 = Integer.parseInt(args[1]);
								int id2 = Integer.parseInt(args[2]);
								fullscreenInterfaceID = id1;
								openInterfaceId = id2;
								pushMessage("Opened Interface", 0, "");
							} catch (Exception e) {
								pushMessage("Interface Failed to load", 0, "");
							}
						}

						if (inputString.equals("::music"))
							Configuration.enableMusic = !Configuration.enableMusic;

						if (inputString.equals("::10xhp")) {
							Configuration.tenXHp = !Configuration.tenXHp;
							loadAllOrbs(frameMode == ScreenMode.FIXED ? 0
									: frameWidth - 217);
						}

						if (inputString.equals("::hp"))
							Configuration.hpAboveHeads = !Configuration.hpAboveHeads;

						if (inputString.equals("::names"))
							Configuration.namesAboveHeads = !Configuration.namesAboveHeads;

						if (inputString.equals("::orbs"))
							Configuration.enableOrbs = !Configuration.enableOrbs;

						if (inputString.equals("::fog"))
							Configuration.enableFog = !Configuration.enableFog;

						if (inputString.equals("::fixed")) {
							frameMode(ScreenMode.FIXED);
						}
						if (inputString.equals("::resize")) {
							frameMode(ScreenMode.RESIZABLE);
						}
						if (inputString.equals("::full")) {
							frameMode(ScreenMode.FULLSCREEN);
						}
						if (inputString.equals("::width")) {
							System.out.println(frameWidth);
						}
						if (inputString.equals("::chat")) {
							if (frameMode != ScreenMode.FIXED) {
								changeChatArea = !changeChatArea;
							}
						}
						if (inputString.equals("::tab")) {
							if (frameMode != ScreenMode.FIXED) {
								changeTabArea = !changeTabArea;
							}
						}
						if (inputString.equals("::optab")) {
							if (frameMode != ScreenMode.FIXED) {
								transparentTabArea = !transparentTabArea;
							}
						}
						if (inputString.equals("::height")) {
							System.out.println(frameHeight);
						}
						if (inputString.equals("::data")) {
							Configuration.clientData = !Configuration.clientData;
						}
						if (inputString.equals("::noclip")) {
							for (int k1 = 0; k1 < 4; k1++) {
								for (int i2 = 1; i2 < 103; i2++) {
									for (int k2 = 1; k2 < 103; k2++) {
										aClass11Array1230[k1].adjacencies[i2][k2] = 0;
									}
								}
							}
						}
					}
					if (inputString.startsWith("/"))
						inputString = "::" + inputString;
					if (inputString.startsWith("::")) {
						outgoing.createFrame(103);
						outgoing.writeByte(inputString.length() - 1);
						outgoing.writeString(inputString.substring(2));
					} else {
						String s = inputString.toLowerCase();
						int j2 = 0;
						if (s.startsWith("yellow:")) {
							j2 = 0;
							inputString = inputString.substring(7);
						} else if (s.startsWith("red:")) {
							j2 = 1;
							inputString = inputString.substring(4);
						} else if (s.startsWith("green:")) {
							j2 = 2;
							inputString = inputString.substring(6);
						} else if (s.startsWith("cyan:")) {
							j2 = 3;
							inputString = inputString.substring(5);
						} else if (s.startsWith("purple:")) {
							j2 = 4;
							inputString = inputString.substring(7);
						} else if (s.startsWith("white:")) {
							j2 = 5;
							inputString = inputString.substring(6);
						} else if (s.startsWith("flash1:")) {
							j2 = 6;
							inputString = inputString.substring(7);
						} else if (s.startsWith("flash2:")) {
							j2 = 7;
							inputString = inputString.substring(7);
						} else if (s.startsWith("flash3:")) {
							j2 = 8;
							inputString = inputString.substring(7);
						} else if (s.startsWith("glow1:")) {
							j2 = 9;
							inputString = inputString.substring(6);
						} else if (s.startsWith("glow2:")) {
							j2 = 10;
							inputString = inputString.substring(6);
						} else if (s.startsWith("glow3:")) {
							j2 = 11;
							inputString = inputString.substring(6);
						}
						s = inputString.toLowerCase();
						int i3 = 0;
						if (s.startsWith("wave:")) {
							i3 = 1;
							inputString = inputString.substring(5);
						} else if (s.startsWith("wave2:")) {
							i3 = 2;
							inputString = inputString.substring(6);
						} else if (s.startsWith("shake:")) {
							i3 = 3;
							inputString = inputString.substring(6);
						} else if (s.startsWith("scroll:")) {
							i3 = 4;
							inputString = inputString.substring(7);
						} else if (s.startsWith("slide:")) {
							i3 = 5;
							inputString = inputString.substring(6);
						}
						outgoing.createFrame(4);
						outgoing.writeByte(0);
						int j3 = outgoing.currentPosition;
						outgoing.writeByteS(i3);
						outgoing.writeByteS(j2);
						aStream_834.currentPosition = 0;
						TextInput.method526(inputString, aStream_834);
						outgoing.writeReverseDataA(aStream_834.payload, 0,
								aStream_834.currentPosition);
						outgoing.writeBytes(outgoing.currentPosition - j3);
						inputString = TextInput.processText(inputString);
						// inputString = Censor.doCensor(inputString);
						localPlayer.spokenText = inputString;
						localPlayer.textColour = j2;
						localPlayer.textEffect = i3;
						localPlayer.textCycle = 150;
						if (myPrivilege == 2)
							pushMessage(localPlayer.spokenText, 2, "@cr2@"
									+ localPlayer.name);
						else if (myPrivilege == 1)
							pushMessage(localPlayer.spokenText, 2, "@cr1@"
									+ localPlayer.name);
						else
							pushMessage(localPlayer.spokenText, 2,
									localPlayer.name);
						if (publicChatMode == 2) {
							publicChatMode = 3;
							outgoing.createFrame(95);
							outgoing.writeByte(publicChatMode);
							outgoing.writeByte(privateChatMode);
							outgoing.writeByte(tradeMode);
						}
					}
					inputString = "";
					inputTaken = true;
				}
			}
		} while (true);
	}

	private void buildPublicChat(int j) {
		int l = 0;
		for (int i1 = 0; i1 < 500; i1++) {
			if (chatMessages[i1] == null)
				continue;
			if (chatTypeView != 1)
				continue;
			int j1 = chatTypes[i1];
			String s = chatNames[i1];
			int k1 = (70 - l * 14 + 42) + anInt1089 + 4 + 5;
			if (k1 < -23)
				break;
			if (s != null && s.startsWith("@cr1@"))
				s = s.substring(5);
			if (s != null && s.startsWith("@cr2@"))
				s = s.substring(5);
			if (s != null && s.startsWith("@cr3@"))
				s = s.substring(5);
			if ((j1 == 1 || j1 == 2)
					&& (j1 == 1 || publicChatMode == 0 || publicChatMode == 1
							&& isFriendOrSelf(s))) {
				if (j > k1 - 14 && j <= k1 && !s.equals(localPlayer.name)) {
					if (myPrivilege >= 1) {
						menuActionName[menuActionRow] = "Report abuse @whi@"
								+ s;
						menuActionID[menuActionRow] = 606;
						menuActionRow++;
					}
					menuActionName[menuActionRow] = "Add ignore @whi@" + s;
					menuActionID[menuActionRow] = 42;
					menuActionRow++;
					menuActionName[menuActionRow] = "Add friend @whi@" + s;
					menuActionID[menuActionRow] = 337;
					menuActionRow++;
				}
				l++;
			}
		}
	}

	private void buildFriendChat(int j) {
		int l = 0;
		for (int i1 = 0; i1 < 500; i1++) {
			if (chatMessages[i1] == null)
				continue;
			if (chatTypeView != 2)
				continue;
			int j1 = chatTypes[i1];
			String s = chatNames[i1];
			int k1 = (70 - l * 14 + 42) + anInt1089 + 4 + 5;
			if (k1 < -23)
				break;
			if (s != null && s.startsWith("@cr1@"))
				s = s.substring(5);
			if (s != null && s.startsWith("@cr2@"))
				s = s.substring(5);
			if (s != null && s.startsWith("@cr3@"))
				s = s.substring(5);
			if ((j1 == 5 || j1 == 6)
					&& (splitPrivateChat == 0 || chatTypeView == 2)
					&& (j1 == 6 || privateChatMode == 0 || privateChatMode == 1
							&& isFriendOrSelf(s)))
				l++;
			if ((j1 == 3 || j1 == 7)
					&& (splitPrivateChat == 0 || chatTypeView == 2)
					&& (j1 == 7 || privateChatMode == 0 || privateChatMode == 1
							&& isFriendOrSelf(s))) {
				if (j > k1 - 14 && j <= k1) {
					if (myPrivilege >= 1) {
						menuActionName[menuActionRow] = "Report abuse @whi@"
								+ s;
						menuActionID[menuActionRow] = 606;
						menuActionRow++;
					}
					menuActionName[menuActionRow] = "Add ignore @whi@" + s;
					menuActionID[menuActionRow] = 42;
					menuActionRow++;
					menuActionName[menuActionRow] = "Add friend @whi@" + s;
					menuActionID[menuActionRow] = 337;
					menuActionRow++;
				}
				l++;
			}
		}
	}

	private void buildDuelorTrade(int j) {
		int l = 0;
		for (int i1 = 0; i1 < 500; i1++) {
			if (chatMessages[i1] == null)
				continue;
			if (chatTypeView != 3 && chatTypeView != 4)
				continue;
			int j1 = chatTypes[i1];
			String s = chatNames[i1];
			int k1 = (70 - l * 14 + 42) + anInt1089 + 4 + 5;
			if (k1 < -23)
				break;
			if (s != null && s.startsWith("@cr1@"))
				s = s.substring(5);
			if (s != null && s.startsWith("@cr2@"))
				s = s.substring(5);
			if (s != null && s.startsWith("@cr3@"))
				s = s.substring(5);
			if (chatTypeView == 3 && j1 == 4
					&& (tradeMode == 0 || tradeMode == 1 && isFriendOrSelf(s))) {
				if (j > k1 - 14 && j <= k1) {
					menuActionName[menuActionRow] = "Accept trade @whi@" + s;
					menuActionID[menuActionRow] = 484;
					menuActionRow++;
				}
				l++;
			}
			if (chatTypeView == 4 && j1 == 8
					&& (tradeMode == 0 || tradeMode == 1 && isFriendOrSelf(s))) {
				if (j > k1 - 14 && j <= k1) {
					menuActionName[menuActionRow] = "Accept challenge @whi@"
							+ s;
					menuActionID[menuActionRow] = 6;
					menuActionRow++;
				}
				l++;
			}
			if (j1 == 12) {
				if (j > k1 - 14 && j <= k1) {
					menuActionName[menuActionRow] = "Go-to @blu@" + s;
					menuActionID[menuActionRow] = 915;
					menuActionRow++;
				}
				l++;
			}
		}
	}

	private void buildChatAreaMenu(int j) {
		int l = 0;
		for (int i1 = 0; i1 < 500; i1++) {
			if (chatMessages[i1] == null)
				continue;
			int j1 = chatTypes[i1];
			int k1 = (70 - l * 14 + 42) + anInt1089 + 4 + 5;
			String s = chatNames[i1];
			if (chatTypeView == 1) {
				buildPublicChat(j);
				break;
			}
			if (chatTypeView == 2) {
				buildFriendChat(j);
				break;
			}
			if (chatTypeView == 3 || chatTypeView == 4) {
				buildDuelorTrade(j);
				break;
			}
			if (chatTypeView == 5) {
				break;
			}
			if (s != null && s.startsWith("@cr1@")) {
				s = s.substring(5);
			}
			if (s != null && s.startsWith("@cr2@")) {
				s = s.substring(5);
			}
			if (s != null && s.startsWith("@cr3@")) {
				s = s.substring(5);
			}
			if (j1 == 0)
				l++;
			if ((j1 == 1 || j1 == 2)
					&& (j1 == 1 || publicChatMode == 0 || publicChatMode == 1
							&& isFriendOrSelf(s))) {
				if (j > k1 - 14 && j <= k1 && !s.equals(localPlayer.name)) {
					if (myPrivilege >= 1) {
						menuActionName[menuActionRow] = "Report abuse @whi@"
								+ s;
						menuActionID[menuActionRow] = 606;
						menuActionRow++;
					}
					menuActionName[menuActionRow] = "Add ignore @whi@" + s;
					menuActionID[menuActionRow] = 42;
					menuActionRow++;
					menuActionName[menuActionRow] = "Add friend @whi@" + s;
					menuActionID[menuActionRow] = 337;
					menuActionRow++;
				}
				l++;
			}
			if ((j1 == 3 || j1 == 7)
					&& splitPrivateChat == 0
					&& (j1 == 7 || privateChatMode == 0 || privateChatMode == 1
							&& isFriendOrSelf(s))) {
				if (j > k1 - 14 && j <= k1) {
					if (myPrivilege >= 1) {
						menuActionName[menuActionRow] = "Report abuse @whi@"
								+ s;
						menuActionID[menuActionRow] = 606;
						menuActionRow++;
					}
					menuActionName[menuActionRow] = "Add ignore @whi@" + s;
					menuActionID[menuActionRow] = 42;
					menuActionRow++;
					menuActionName[menuActionRow] = "Add friend @whi@" + s;
					menuActionID[menuActionRow] = 337;
					menuActionRow++;
				}
				l++;
			}
			if (j1 == 4
					&& (tradeMode == 0 || tradeMode == 1 && isFriendOrSelf(s))) {
				if (j > k1 - 14 && j <= k1) {
					menuActionName[menuActionRow] = "Accept trade @whi@" + s;
					menuActionID[menuActionRow] = 484;
					menuActionRow++;
				}
				l++;
			}
			if ((j1 == 5 || j1 == 6) && splitPrivateChat == 0
					&& privateChatMode < 2)
				l++;
			if (j1 == 8
					&& (tradeMode == 0 || tradeMode == 1 && isFriendOrSelf(s))) {
				if (j > k1 - 14 && j <= k1) {
					menuActionName[menuActionRow] = "Accept challenge @whi@"
							+ s;
					menuActionID[menuActionRow] = 6;
					menuActionRow++;
				}
				l++;
			}
		}
	}

	public int getXPForLevel(int level) {
		int points = 0;
		int output = 0;
		for (int lvl = 1; lvl <= level; lvl++) {
			points += Math.floor(lvl + 300.0 * Math.pow(2.0, lvl / 7.0));
			if (lvl >= level) {
				return output;
			}
			output = (int) Math.floor(points / 4);
		}
		return 0;
	}

	private void drawFriendsListOrWelcomeScreen(Widget class9) {
		int j = class9.contentType;
		if (j >= 1 && j <= 100 || j >= 701 && j <= 800) {
			if (j == 1 && friendServerStatus == 0) {
				class9.defaultText = "Loading friend list";
				class9.optionType = 0;
				return;
			}
			if (j == 1 && friendServerStatus == 1) {
				class9.defaultText = "Connecting to friendserver";
				class9.optionType = 0;
				return;
			}
			if (j == 2 && friendServerStatus != 2) {
				class9.defaultText = "Please wait...";
				class9.optionType = 0;
				return;
			}
			int k = friendsCount;
			if (friendServerStatus != 2)
				k = 0;
			if (j > 700)
				j -= 601;
			else
				j--;
			if (j >= k) {
				class9.defaultText = "";
				class9.optionType = 0;
				return;
			} else {
				class9.defaultText = friendsList[j];
				class9.optionType = 1;
				return;
			}
		}
		if (j >= 101 && j <= 200 || j >= 801 && j <= 900) {
			int l = friendsCount;
			if (friendServerStatus != 2)
				l = 0;
			if (j > 800)
				j -= 701;
			else
				j -= 101;
			if (j >= l) {
				class9.defaultText = "";
				class9.optionType = 0;
				return;
			}
			if (friendsNodeIDs[j] == 0)
				class9.defaultText = "@red@Offline";
			else if (friendsNodeIDs[j] == nodeID)
				class9.defaultText = "@gre@Online"/* + (friendsNodeIDs[j] - 9) */;
			else
				class9.defaultText = "@red@Offline"/* + (friendsNodeIDs[j] - 9) */;
			class9.optionType = 1;
			return;
		}

		/** Skill Tab **/
		if (j == 210) {
			class9.defaultText = "Total XP: "
					+ NumberFormat.getIntegerInstance().format(xpCounter);
			return;
		}
		if (j == 211) {
			if (maxStats[0] == 99) {
				class9.defaultText = "Attack XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[0]);
				return;
			} else {
				class9.defaultText = "Attack XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[0])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[0] + 1));

			}
			return;
		}
		if (j == 212) {
			if (maxStats[2] == 99) {
				class9.defaultText = "Strength XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[2]);
				return;
			} else {
				class9.defaultText = "Strength XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[2])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[2] + 1));

			}
			return;
		}
		if (j == 213) {
			if (maxStats[3] == 99) {
				class9.defaultText = "Hitpoints XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[3]);
				return;
			} else {
				class9.defaultText = "Hitpoints XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[3])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[3] + 1));

			}
			return;
		}
		if (j == 214) {
			if (maxStats[1] == 99) {
				class9.defaultText = "Defence XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[1]);
				return;
			} else {
				class9.defaultText = "Defence XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[1])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[1] + 1));

			}
			return;
		}
		if (j == 215) {
			if (maxStats[4] == 99) {
				class9.defaultText = "Ranged XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[4]);
				return;
			} else {
				class9.defaultText = "Ranged XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[4])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[4] + 1));

			}
			return;
		}
		if (j == 216) {
			if (maxStats[5] == 99) {
				class9.defaultText = "Prayer XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[5]);
				return;
			} else {
				class9.defaultText = "Prayer XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[5])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[5] + 1));

			}
			return;
		}
		if (j == 217) {
			if (maxStats[6] == 99) {
				class9.defaultText = "Magic XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[6]);
				return;
			} else {
				class9.defaultText = "Magic XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[6])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[6] + 1));

			}
			return;
		}
		if (j == 218) {
			if (maxStats[7] == 99) {
				class9.defaultText = "Cooking XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[7]);
				return;
			} else {
				class9.defaultText = "Cooking XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[7])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[7] + 1));

			}
			return;
		}
		if (j == 219) {
			if (maxStats[8] == 99) {
				class9.defaultText = "Woodcutting XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[8]);
				return;
			} else {
				class9.defaultText = "Woodcutting XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[8])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[8] + 1));

			}
			return;
		}
		if (j == 220) {
			if (maxStats[9] == 99) {
				class9.defaultText = "Fletching XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[9]);
				return;
			} else {
				class9.defaultText = "Fletching XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[9])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[9] + 1));

			}
			return;
		}
		if (j == 221) {
			if (maxStats[10] == 99) {
				class9.defaultText = "Fishing XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[10]);
				return;
			} else {
				class9.defaultText = "Fishing XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[10])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[10] + 1));

			}
			return;
		}
		if (j == 222) {
			if (maxStats[11] == 99) {
				class9.defaultText = "Firemaking XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[11]);
				return;
			} else {
				class9.defaultText = "Firemaking XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[11])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[11] + 1));

			}
			return;
		}
		if (j == 223) {
			if (maxStats[12] == 99) {
				class9.defaultText = "Crafting XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[12]);
				return;
			} else {
				class9.defaultText = "Crafting XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[12])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[12] + 1));

			}
			return;
		}
		if (j == 224) {
			if (maxStats[13] == 99) {
				class9.defaultText = "Smithing XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[13]);
				return;
			} else {
				class9.defaultText = "Smithing XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[13])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[13] + 1));

			}
			return;
		}
		if (j == 225) {
			if (maxStats[14] == 99) {
				class9.defaultText = "Mining XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[14]);
				return;
			} else {
				class9.defaultText = "Mining XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[14])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[14] + 1));

			}
			return;
		}
		if (j == 226) {
			if (maxStats[15] == 99) {
				class9.defaultText = "Herblore XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[15]);
				return;
			} else {
				class9.defaultText = "Herblore XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[15])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[15] + 1));

			}
			return;
		}
		if (j == 227) {
			if (maxStats[16] == 99) {
				class9.defaultText = "Agility XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[16]);
				return;
			} else {
				class9.defaultText = "Agility XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[16])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[16] + 1));

			}
			return;
		}
		if (j == 228) {
			if (maxStats[17] == 99) {
				class9.defaultText = "Thieving XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[17]);
				return;
			} else {
				class9.defaultText = "Thieving XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[17])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[17] + 1));

			}
			return;
		}
		if (j == 229) {
			if (maxStats[18] == 99) {
				class9.defaultText = "Slayer XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[18]);
				return;
			} else {
				class9.defaultText = "Slayer XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[18])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[18] + 1));

			}
			return;
		}
		if (j == 230) {
			if (maxStats[19] == 99) {
				class9.defaultText = "Farming XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[19]);
				return;
			} else {
				class9.defaultText = "Farming XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[19])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[19] + 1));

			}
			return;
		}
		if (j == 231) {
			if (maxStats[20] == 99) {
				class9.defaultText = "Runecrafting XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[20]);
				return;
			} else {
				class9.defaultText = "Runecrafting XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[20])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[20] + 1));

			}
			return;
		}
		if (j == 232) {
			if (maxStats[21] == 99) {
				class9.defaultText = "Hunter XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[21]);
				return;
			} else {
				class9.defaultText = "Hunter XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[21])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[21] + 1));

			}
			return;
		}
		if (j == 233) {
			if (maxStats[22] == 99) {
				class9.defaultText = "Construction XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[22]);
				return;
			} else {
				class9.defaultText = "Construction XP: "
						+ NumberFormat.getIntegerInstance().format(
								currentExp[22])
						+ "\\nNext Level at: "
						+ NumberFormat.getIntegerInstance().format(
								getXPForLevel(maxStats[22] + 1));

			}
			return;
		}

		if (j == 203) {
			int i1 = friendsCount;
			if (friendServerStatus != 2)
				i1 = 0;
			class9.scrollMax = i1 * 15 + 20;
			if (class9.scrollMax <= class9.height)
				class9.scrollMax = class9.height + 1;
			return;
		}
		if (j >= 401 && j <= 500) {
			if ((j -= 401) == 0 && friendServerStatus == 0) {
				class9.defaultText = "Loading ignore list";
				class9.optionType = 0;
				return;
			}
			if (j == 1 && friendServerStatus == 0) {
				class9.defaultText = "Please wait...";
				class9.optionType = 0;
				return;
			}
			int j1 = ignoreCount;
			if (friendServerStatus == 0)
				j1 = 0;
			if (j >= j1) {
				class9.defaultText = "";
				class9.optionType = 0;
				return;
			} else {
				class9.defaultText = TextClass.fixName(TextClass
						.nameForLong(ignoreListAsLongs[j]));
				class9.optionType = 1;
				return;
			}
		}
		if (j == 503) {
			class9.scrollMax = ignoreCount * 15 + 20;
			if (class9.scrollMax <= class9.height)
				class9.scrollMax = class9.height + 1;
			return;
		}
		if (j == 327) {
			class9.modelRotation1 = 150;
			class9.modelRotation2 = (int) (Math.sin((double) loopCycle / 40D) * 256D) & 0x7ff;
			if (aBoolean1031) {
				for (int k1 = 0; k1 < 7; k1++) {
					int l1 = anIntArray1065[k1];
					if (l1 >= 0 && !IdentityKit.cache[l1].method537())
						return;
				}

				aBoolean1031 = false;
				Model aclass30_sub2_sub4_sub6s[] = new Model[7];
				int i2 = 0;
				for (int j2 = 0; j2 < 7; j2++) {
					int k2 = anIntArray1065[j2];
					if (k2 >= 0)
						aclass30_sub2_sub4_sub6s[i2++] = IdentityKit.cache[k2]
								.method538();
				}

				Model model = new Model(i2, aclass30_sub2_sub4_sub6s);
				for (int l2 = 0; l2 < 5; l2++)
					if (characterDesignColours[l2] != 0) {
						model.recolor(
								anIntArrayArray1003[l2][0],
								anIntArrayArray1003[l2][characterDesignColours[l2]]);
						if (l2 == 1)
							model.recolor(anIntArray1204[0],
									anIntArray1204[characterDesignColours[l2]]);
					}

				model.skin();
				model.apply(Animation.animations[localPlayer.standAnimIndex].anIntArray353[0]);
				model.light(64, 850, -30, -50, -30, true);
				class9.defaultMediaType = 5;
				class9.defaultMedia = 0;
				Widget.method208(aBoolean994, model);
			}
			return;
		}
		if (j == 328) {
			Widget rsInterface = class9;
			int verticleTilt = 150;
			int animationSpeed = (int) (Math.sin((double) loopCycle / 40D) * 256D) & 0x7ff;
			rsInterface.modelRotation1 = verticleTilt;
			rsInterface.modelRotation2 = animationSpeed;
			if (aBoolean1031) {
				Model characterDisplay = localPlayer.method452();
				for (int l2 = 0; l2 < 5; l2++)
					if (characterDesignColours[l2] != 0) {
						characterDisplay
								.recolor(
										anIntArrayArray1003[l2][0],
										anIntArrayArray1003[l2][characterDesignColours[l2]]);
						if (l2 == 1)
							characterDisplay.recolor(anIntArray1204[0],
									anIntArray1204[characterDesignColours[l2]]);
					}
				int staticFrame = localPlayer.standAnimIndex;
				characterDisplay.skin();
				characterDisplay
						.apply(Animation.animations[staticFrame].anIntArray353[0]);
				// characterDisplay.method479(64, 850, -30, -50, -30, true);
				rsInterface.defaultMediaType = 5;
				rsInterface.defaultMedia = 0;
				Widget.method208(aBoolean994, characterDisplay);
			}
			return;
		}
		if (j == 324) {
			if (aClass30_Sub2_Sub1_Sub1_931 == null) {
				aClass30_Sub2_Sub1_Sub1_931 = class9.disabledSprite;
				aClass30_Sub2_Sub1_Sub1_932 = class9.enabledSprite;
			}
			if (maleCharacter) {
				class9.disabledSprite = aClass30_Sub2_Sub1_Sub1_932;
				return;
			} else {
				class9.disabledSprite = aClass30_Sub2_Sub1_Sub1_931;
				return;
			}
		}
		if (j == 325) {
			if (aClass30_Sub2_Sub1_Sub1_931 == null) {
				aClass30_Sub2_Sub1_Sub1_931 = class9.disabledSprite;
				aClass30_Sub2_Sub1_Sub1_932 = class9.enabledSprite;
			}
			if (maleCharacter) {
				class9.disabledSprite = aClass30_Sub2_Sub1_Sub1_931;
				return;
			} else {
				class9.disabledSprite = aClass30_Sub2_Sub1_Sub1_932;
				return;
			}
		}
		if (j == 600) {
			class9.defaultText = reportAbuseInput;
			if (loopCycle % 20 < 10) {
				class9.defaultText += "|";
				return;
			} else {
				class9.defaultText += " ";
				return;
			}
		}
		if (j == 613)
			if (myPrivilege >= 1) {
				if (canMute) {
					class9.textColor = 0xff0000;
					class9.defaultText = "Moderator option: Mute player for 48 hours: <ON>";
				} else {
					class9.textColor = 0xffffff;
					class9.defaultText = "Moderator option: Mute player for 48 hours: <OFF>";
				}
			} else {
				class9.defaultText = "";
			}
		if (j == 650 || j == 655)
			if (anInt1193 != 0) {
				String s;
				if (daysSinceLastLogin == 0)
					s = "earlier today";
				else if (daysSinceLastLogin == 1)
					s = "yesterday";
				else
					s = daysSinceLastLogin + " days ago";
				class9.defaultText = "You last logged in " + s + " from: "
						+ Signlink.dns;
			} else {
				class9.defaultText = "";
			}
		if (j == 651) {
			if (unreadMessages == 0) {
				class9.defaultText = "0 unread messages";
				class9.textColor = 0xffff00;
			}
			if (unreadMessages == 1) {
				class9.defaultText = "1 unread defaultText";
				class9.textColor = 65280;
			}
			if (unreadMessages > 1) {
				class9.defaultText = unreadMessages + " unread messages";
				class9.textColor = 65280;
			}
		}
		if (j == 652)
			if (daysSinceRecovChange == 201) {
				if (membersInt == 1)
					class9.defaultText = "@yel@This is a non-members world: @whi@Since you are a member we";
				else
					class9.defaultText = "";
			} else if (daysSinceRecovChange == 200) {
				class9.defaultText = "You have not yet set any password recovery questions.";
			} else {
				String s1;
				if (daysSinceRecovChange == 0)
					s1 = "Earlier today";
				else if (daysSinceRecovChange == 1)
					s1 = "Yesterday";
				else
					s1 = daysSinceRecovChange + " days ago";
				class9.defaultText = s1
						+ " you changed your recovery questions";
			}
		if (j == 653)
			if (daysSinceRecovChange == 201) {
				if (membersInt == 1)
					class9.defaultText = "@whi@recommend you use a members world instead. You may use";
				else
					class9.defaultText = "";
			} else if (daysSinceRecovChange == 200)
				class9.defaultText = "We strongly recommend you do so now to secure your account.";
			else
				class9.defaultText = "If you do not remember making this change then cancel it immediately";
		if (j == 654) {
			if (daysSinceRecovChange == 201)
				if (membersInt == 1) {
					class9.defaultText = "@whi@this world but member benefits are unavailable whilst here.";
					return;
				} else {
					class9.defaultText = "";
					return;
				}
			if (daysSinceRecovChange == 200) {
				class9.defaultText = "Do this from the 'account management' area on our front webpage";
				return;
			}
			class9.defaultText = "Do this from the 'account management' area on our front webpage";
		}
	}

	private void drawSplitPrivateChat() {
		if (splitPrivateChat == 0) {
			return;
		}
		GameFont textDrawingArea = regularText;
		int i = 0;
		if (systemUpdateTime != 0) {
			i = 1;
		}
		for (int j = 0; j < 100; j++) {
			if (chatMessages[j] != null) {
				int k = chatTypes[j];
				String s = chatNames[j];
				byte byte1 = 0;
				if (s != null && s.startsWith("@cr1@")) {
					s = s.substring(5);
					byte1 = 1;
				}
				if (s != null && s.startsWith("@cr2@")) {
					s = s.substring(5);
					byte1 = 2;
				}
				if ((k == 3 || k == 7)
						&& (k == 7 || privateChatMode == 0 || privateChatMode == 1
								&& isFriendOrSelf(s))) {
					int l = 329 - i * 13;
					if (frameMode != ScreenMode.FIXED) {
						l = frameHeight - 170 - i * 13;
					}
					int k1 = 4;
					textDrawingArea.method385(0, "From", l, k1);
					textDrawingArea.method385(65535, "From", l - 1, k1);
					k1 += textDrawingArea.getTextWidth("From ");
					if (byte1 == 1) {
						modIcons[0].drawSprite(k1, l - 12);
						k1 += 12;
					}
					if (byte1 == 2) {
						modIcons[1].drawSprite(k1, l - 12);
						k1 += 12;
					}
					textDrawingArea.method385(0, s + ": " + chatMessages[j], l,
							k1);
					textDrawingArea.method385(65535,
							s + ": " + chatMessages[j], l - 1, k1);
					if (++i >= 5) {
						return;
					}
				}
				if (k == 5 && privateChatMode < 2) {
					int i1 = 329 - i * 13;
					if (frameMode != ScreenMode.FIXED) {
						i1 = frameHeight - 170 - i * 13;
					}
					textDrawingArea.method385(0, chatMessages[j], i1, 4);
					textDrawingArea
							.method385(65535, chatMessages[j], i1 - 1, 4);
					if (++i >= 5) {
						return;
					}
				}
				if (k == 6 && privateChatMode < 2) {
					int j1 = 329 - i * 13;
					if (frameMode != ScreenMode.FIXED) {
						j1 = frameHeight - 170 - i * 13;
					}
					textDrawingArea.method385(0, "To " + s + ": "
							+ chatMessages[j], j1, 4);
					textDrawingArea.method385(65535, "To " + s + ": "
							+ chatMessages[j], j1 - 1, 4);
					if (++i >= 5) {
						return;
					}
				}
			}
		}
	}

	public void pushMessage(String s, int i, String s1) {
		if (i == 0 && dialogueId != -1) {
			clickToContinueString = s;
			super.clickMode3 = 0;
		}
		if (backDialogueId == -1)
			inputTaken = true;
		for (int j = 499; j > 0; j--) {
			chatTypes[j] = chatTypes[j - 1];
			chatNames[j] = chatNames[j - 1];
			chatMessages[j] = chatMessages[j - 1];
			chatRights[j] = chatRights[j - 1];
		}
		chatTypes[0] = i;
		chatNames[0] = s1;
		chatMessages[0] = s;
		chatRights[0] = rights;
	}

	public static void setTab(int id) {
		tabID = id;
		tabAreaAltered = true;
	}

	private final void minimapHovers() {
		final boolean fixed = frameMode == ScreenMode.FIXED;
		hpHover = fixed ? hpHover = super.mouseX >= 516 && super.mouseX <= 571
				&& super.mouseY >= 41 && super.mouseY < 72
				: super.mouseX >= frameWidth - 216 && super.mouseX <= 159
						&& super.mouseY >= 13 && super.mouseY < 47;
		prayHover = fixed ? prayHover = super.mouseX >= 518
				&& super.mouseX <= 572 && super.mouseY >= 85
				&& super.mouseY < 117 : super.mouseX >= frameWidth - 207
				&& super.mouseX <= frameWidth - 151 && super.mouseY >= 105
				&& super.mouseY < 139;
		runHover = fixed ? runHover = super.mouseX >= 540
				&& super.mouseX <= 593 && super.mouseY >= 123
				&& super.mouseY < 154 : super.mouseX >= frameWidth - 174
				&& super.mouseX <= frameWidth - 120 && super.mouseY >= 132
				&& super.mouseY < 165;
		counterHover = fixed ? super.mouseX >= 519 && super.mouseX <= 536
				&& super.mouseY >= 22 && super.mouseY <= 41
				: super.mouseX >= frameWidth - 186
						&& super.mouseX <= frameWidth - 158
						&& super.mouseY >= 41 && super.mouseY <= 65;
		worldHover = fixed ? super.mouseX >= 718 && super.mouseX <= 748
				&& super.mouseY >= 22 && super.mouseY <= 50
				: super.mouseX >= frameWidth - 117
						&& super.mouseX <= frameWidth - 86
						&& super.mouseY >= 153 && super.mouseY <= 186;
		specialHover = fixed ? super.mouseX >= 670 && super.mouseX <= 727
				&& super.mouseY >= 133 && super.mouseY <= 164
				: super.mouseX >= frameWidth - 62
						&& super.mouseX <= frameWidth - 5
						&& super.mouseY >= 151 && super.mouseY <= 184;
	}

	private final int[] tabClickX = { 38, 33, 33, 33, 33, 33, 38, 38, 33, 33,
			33, 33, 33, 38 }, tabClickStart = { 522, 560, 593, 625, 659, 692,
			724, 522, 560, 593, 625, 659, 692, 724 }, tabClickY = { 169, 169,
			169, 169, 169, 169, 169, 466, 466, 466, 466, 466, 466, 466 };

	private void processTabClick() {
		if (super.clickMode3 == 1) {
			if (frameMode == ScreenMode.FIXED || frameMode != ScreenMode.FIXED
					&& !changeTabArea) {
				int xOffset = frameMode == ScreenMode.FIXED ? 0
						: frameWidth - 765;
				int yOffset = frameMode == ScreenMode.FIXED ? 0
						: frameHeight - 503;
				for (int i = 0; i < tabClickX.length; i++) {
					if (super.mouseX >= tabClickStart[i] + xOffset
							&& super.mouseX <= tabClickStart[i] + tabClickX[i]
									+ xOffset
							&& super.mouseY >= tabClickY[i] + yOffset
							&& super.mouseY < tabClickY[i] + 37 + yOffset
							&& tabInterfaceIDs[i] != -1) {
						tabID = i;
						tabAreaAltered = true;
						break;
					}
				}
			} else if (changeTabArea && frameWidth < 1000) {
				if (super.saveClickX >= frameWidth - 226
						&& super.saveClickX <= frameWidth - 195
						&& super.saveClickY >= frameHeight - 72
						&& super.saveClickY < frameHeight - 40
						&& tabInterfaceIDs[0] != -1) {
					if (tabID == 0) {
						showTabComponents = !showTabComponents;
					} else {
						showTabComponents = true;
					}
					tabID = 0;
					tabAreaAltered = true;

				}
				if (super.saveClickX >= frameWidth - 194
						&& super.saveClickX <= frameWidth - 163
						&& super.saveClickY >= frameHeight - 72
						&& super.saveClickY < frameHeight - 40
						&& tabInterfaceIDs[1] != -1) {
					if (tabID == 1) {
						showTabComponents = !showTabComponents;
					} else {
						showTabComponents = true;
					}
					tabID = 1;
					tabAreaAltered = true;

				}
				if (super.saveClickX >= frameWidth - 162
						&& super.saveClickX <= frameWidth - 131
						&& super.saveClickY >= frameHeight - 72
						&& super.saveClickY < frameHeight - 40
						&& tabInterfaceIDs[2] != -1) {
					if (tabID == 2) {
						showTabComponents = !showTabComponents;
					} else {
						showTabComponents = true;
					}
					tabID = 2;
					tabAreaAltered = true;

				}
				if (super.saveClickX >= frameWidth - 129
						&& super.saveClickX <= frameWidth - 98
						&& super.saveClickY >= frameHeight - 72
						&& super.saveClickY < frameHeight - 40
						&& tabInterfaceIDs[3] != -1) {
					if (tabID == 3) {
						showTabComponents = !showTabComponents;
					} else {
						showTabComponents = true;
					}
					tabID = 3;
					tabAreaAltered = true;

				}
				if (super.saveClickX >= frameWidth - 97
						&& super.saveClickX <= frameWidth - 66
						&& super.saveClickY >= frameHeight - 72
						&& super.saveClickY < frameHeight - 40
						&& tabInterfaceIDs[4] != -1) {
					if (tabID == 4) {
						showTabComponents = !showTabComponents;
					} else {
						showTabComponents = true;
					}
					tabID = 4;
					tabAreaAltered = true;

				}
				if (super.saveClickX >= frameWidth - 65
						&& super.saveClickX <= frameWidth - 34
						&& super.saveClickY >= frameHeight - 72
						&& super.saveClickY < frameHeight - 40
						&& tabInterfaceIDs[5] != -1) {
					if (tabID == 5) {
						showTabComponents = !showTabComponents;
					} else {
						showTabComponents = true;
					}
					tabID = 5;
					tabAreaAltered = true;

				}
				if (super.saveClickX >= frameWidth - 33
						&& super.saveClickX <= frameWidth
						&& super.saveClickY >= frameHeight - 72
						&& super.saveClickY < frameHeight - 40
						&& tabInterfaceIDs[6] != -1) {
					if (tabID == 6) {
						showTabComponents = !showTabComponents;
					} else {
						showTabComponents = true;
					}
					tabID = 6;
					tabAreaAltered = true;

				}

				if (super.saveClickX >= frameWidth - 194
						&& super.saveClickX <= frameWidth - 163
						&& super.saveClickY >= frameHeight - 37
						&& super.saveClickY < frameHeight - 0
						&& tabInterfaceIDs[8] != -1) {
					if (tabID == 8) {
						showTabComponents = !showTabComponents;
					} else {
						showTabComponents = true;
					}
					tabID = 8;
					tabAreaAltered = true;

				}
				if (super.saveClickX >= frameWidth - 162
						&& super.saveClickX <= frameWidth - 131
						&& super.saveClickY >= frameHeight - 37
						&& super.saveClickY < frameHeight - 0
						&& tabInterfaceIDs[9] != -1) {
					if (tabID == 9) {
						showTabComponents = !showTabComponents;
					} else {
						showTabComponents = true;
					}
					tabID = 9;
					tabAreaAltered = true;

				}
				if (super.saveClickX >= frameWidth - 129
						&& super.saveClickX <= frameWidth - 98
						&& super.saveClickY >= frameHeight - 37
						&& super.saveClickY < frameHeight - 0
						&& tabInterfaceIDs[10] != -1) {
					if (tabID == 7) {
						showTabComponents = !showTabComponents;
					} else {
						showTabComponents = true;
					}
					tabID = 7;
					tabAreaAltered = true;

				}
				if (super.saveClickX >= frameWidth - 97
						&& super.saveClickX <= frameWidth - 66
						&& super.saveClickY >= frameHeight - 37
						&& super.saveClickY < frameHeight - 0
						&& tabInterfaceIDs[11] != -1) {
					if (tabID == 11) {
						showTabComponents = !showTabComponents;
					} else {
						showTabComponents = true;
					}
					tabID = 11;
					tabAreaAltered = true;

				}
				if (super.saveClickX >= frameWidth - 65
						&& super.saveClickX <= frameWidth - 34
						&& super.saveClickY >= frameHeight - 37
						&& super.saveClickY < frameHeight - 0
						&& tabInterfaceIDs[12] != -1) {
					if (tabID == 12) {
						showTabComponents = !showTabComponents;
					} else {
						showTabComponents = true;
					}
					tabID = 12;
					tabAreaAltered = true;

				}
				if (super.saveClickX >= frameWidth - 33
						&& super.saveClickX <= frameWidth
						&& super.saveClickY >= frameHeight - 37
						&& super.saveClickY < frameHeight - 0
						&& tabInterfaceIDs[13] != -1) {
					if (tabID == 13) {
						showTabComponents = !showTabComponents;
					} else {
						showTabComponents = true;
					}
					tabID = 13;
					tabAreaAltered = true;

				}
			} else if (changeTabArea && frameWidth >= 1000) {
				if (super.mouseY >= frameHeight - 37
						&& super.mouseY <= frameHeight) {
					if (super.mouseX >= frameWidth - 417
							&& super.mouseX <= frameWidth - 386) {
						if (tabID == 0) {
							showTabComponents = !showTabComponents;
						} else {
							showTabComponents = true;
						}
						tabID = 0;
						tabAreaAltered = true;
					}
					if (super.mouseX >= frameWidth - 385
							&& super.mouseX <= frameWidth - 354) {
						if (tabID == 1) {
							showTabComponents = !showTabComponents;
						} else {
							showTabComponents = true;
						}
						tabID = 1;
						tabAreaAltered = true;
					}
					if (super.mouseX >= frameWidth - 353
							&& super.mouseX <= frameWidth - 322) {
						if (tabID == 2) {
							showTabComponents = !showTabComponents;
						} else {
							showTabComponents = true;
						}
						tabID = 2;
						tabAreaAltered = true;
					}
					if (super.mouseX >= frameWidth - 321
							&& super.mouseX <= frameWidth - 290) {
						if (tabID == 3) {
							showTabComponents = !showTabComponents;
						} else {
							showTabComponents = true;
						}
						tabID = 3;
						tabAreaAltered = true;
					}
					if (super.mouseX >= frameWidth - 289
							&& super.mouseX <= frameWidth - 258) {
						if (tabID == 4) {
							showTabComponents = !showTabComponents;
						} else {
							showTabComponents = true;
						}
						tabID = 4;
						tabAreaAltered = true;
					}
					if (super.mouseX >= frameWidth - 257
							&& super.mouseX <= frameWidth - 226) {
						if (tabID == 5) {
							showTabComponents = !showTabComponents;
						} else {
							showTabComponents = true;
						}
						tabID = 5;
						tabAreaAltered = true;
					}
					if (super.mouseX >= frameWidth - 225
							&& super.mouseX <= frameWidth - 194) {
						if (tabID == 6) {
							showTabComponents = !showTabComponents;
						} else {
							showTabComponents = true;
						}
						tabID = 6;
						tabAreaAltered = true;
					}
					if (super.mouseX >= frameWidth - 193
							&& super.mouseX <= frameWidth - 163) {
						if (tabID == 8) {
							showTabComponents = !showTabComponents;
						} else {
							showTabComponents = true;
						}
						tabID = 8;
						tabAreaAltered = true;
					}
					if (super.mouseX >= frameWidth - 162
							&& super.mouseX <= frameWidth - 131) {
						if (tabID == 9) {
							showTabComponents = !showTabComponents;
						} else {
							showTabComponents = true;
						}
						tabID = 9;
						tabAreaAltered = true;
					}
					if (super.mouseX >= frameWidth - 130
							&& super.mouseX <= frameWidth - 99) {
						if (tabID == 7) {
							showTabComponents = !showTabComponents;
						} else {
							showTabComponents = true;
						}
						tabID = 7;
						tabAreaAltered = true;
					}
					if (super.mouseX >= frameWidth - 98
							&& super.mouseX <= frameWidth - 67) {
						if (tabID == 11) {
							showTabComponents = !showTabComponents;
						} else {
							showTabComponents = true;
						}
						tabID = 11;
						tabAreaAltered = true;
					}
					if (super.mouseX >= frameWidth - 66
							&& super.mouseX <= frameWidth - 45) {
						if (tabID == 12) {
							showTabComponents = !showTabComponents;
						} else {
							showTabComponents = true;
						}
						tabID = 12;
						tabAreaAltered = true;
					}
					if (super.mouseX >= frameWidth - 31
							&& super.mouseX <= frameWidth) {
						if (tabID == 13) {
							showTabComponents = !showTabComponents;
						} else {
							showTabComponents = true;
						}
						tabID = 13;
						tabAreaAltered = true;
					}
				}
			}
		}
	}

	private void setupGameplayScreen() {
		if (chatboxImageProducer != null)
			return;
		nullLoader();
		super.fullGameScreen = null;
		topLeft1BackgroundTile = null;
		bottomLeft1BackgroundTile = null;
		loginBoxImageProducer = null;
		loginScreenAccessories = null;
		flameLeftBackground = null;
		flameRightBackground = null;
		bottomLeft0BackgroundTile = null;
		bottomRightImageProducer = null;
		loginMusicImageProducer = null;
		middleLeft1BackgroundTile = null;
		aRSImageProducer_1115 = null;
		chatboxImageProducer = new ImageProducer(519, 165);// chatback
		minimapImageProducer = new ImageProducer(249, 168);// mapback
		Raster.clear();
		cacheSprite[19].drawSprite(0, 0);
		tabImageProducer = new ImageProducer(249, 335);// inventory
		gameScreenImageProducer = new ImageProducer(512, 334);// gamescreen
		Raster.clear();
		chatSettingImageProducer = new ImageProducer(249, 45);
		welcomeScreenRaised = true;
	}

	private void refreshMinimap(Sprite sprite, int j, int k) {
		int l = k * k + j * j;
		if (l > 4225 && l < 0x15f90) {
			int i1 = cameraHorizontal + minimapRotation & 0x7ff;
			int j1 = Model.SINE[i1];
			int k1 = Model.COSINE[i1];
			j1 = (j1 * 256) / (minimapZoom + 256);
			k1 = (k1 * 256) / (minimapZoom + 256);
		} else {
			markMinimap(sprite, k, j);
		}
	}

	public void rightClickChatButtons() {
		if (mouseY >= frameHeight - 22 && mouseY <= frameHeight) {
			if (super.mouseX >= 5 && super.mouseX <= 61) {
				menuActionName[1] = "View All";
				menuActionID[1] = 999;
				menuActionRow = 2;
			} else if (super.mouseX >= 71 && super.mouseX <= 127) {
				menuActionName[1] = "View Game";
				menuActionID[1] = 998;
				menuActionRow = 2;
			} else if (super.mouseX >= 137 && super.mouseX <= 193) {
				menuActionName[1] = "Hide public";
				menuActionID[1] = 997;
				menuActionName[2] = "Off public";
				menuActionID[2] = 996;
				menuActionName[3] = "Friends public";
				menuActionID[3] = 995;
				menuActionName[4] = "On public";
				menuActionID[4] = 994;
				menuActionName[5] = "View public";
				menuActionID[5] = 993;
				menuActionRow = 6;
			} else if (super.mouseX >= 203 && super.mouseX <= 259) {
				menuActionName[1] = "Off private";
				menuActionID[1] = 992;
				menuActionName[2] = "Friends private";
				menuActionID[2] = 991;
				menuActionName[3] = "On private";
				menuActionID[3] = 990;
				menuActionName[4] = "View private";
				menuActionID[4] = 989;
				menuActionRow = 5;
			} else if (super.mouseX >= 269 && super.mouseX <= 325) {
				menuActionName[1] = "Off clan chat";
				menuActionID[1] = 1003;
				menuActionName[2] = "Friends clan chat";
				menuActionID[2] = 1002;
				menuActionName[3] = "On clan chat";
				menuActionID[3] = 1001;
				menuActionName[4] = "View clan chat";
				menuActionID[4] = 1000;
				menuActionRow = 5;
			} else if (super.mouseX >= 335 && super.mouseX <= 391) {
				menuActionName[1] = "Off trade";
				menuActionID[1] = 987;
				menuActionName[2] = "Friends trade";
				menuActionID[2] = 986;
				menuActionName[3] = "On trade";
				menuActionID[3] = 985;
				menuActionName[4] = "View trade";
				menuActionID[4] = 984;
				menuActionRow = 5;
			} else if (super.mouseX >= 404 && super.mouseX <= 515) {
				menuActionName[1] = "Report Abuse";
				menuActionID[1] = 606;
				menuActionRow = 2;
			}
		}
	}

	public void processRightClick() {
		if (activeInterfaceType != 0) {
			return;
		}
		menuActionName[0] = "Cancel";
		menuActionID[0] = 1107;
		menuActionRow = 1;
		if (showChatComponents) {
			buildSplitPrivateChatMenu();
		}
		anInt886 = 0;
		anInt1315 = 0;
		if (frameMode == ScreenMode.FIXED) {
			if (super.mouseX > 4 && super.mouseY > 4 && super.mouseX < 516
					&& super.mouseY < 338) {
				if (openInterfaceId != -1) {
					buildInterfaceMenu(4,
							Widget.interfaceCache[openInterfaceId],
							super.mouseX, 4, super.mouseY, 0);
				} else {
					build3dScreenMenu();
				}
			}
		} else if (frameMode != ScreenMode.FIXED) {
			if (getMousePositions()) {
				if (super.mouseX > (frameWidth / 2) - 356
						&& super.mouseY > (frameHeight / 2) - 230
						&& super.mouseX < ((frameWidth / 2) + 356)
						&& super.mouseY < (frameHeight / 2) + 230
						&& openInterfaceId != -1) {
					buildInterfaceMenu((frameWidth / 2) - 356,
							Widget.interfaceCache[openInterfaceId],
							super.mouseX, (frameHeight / 2) - 230,
							super.mouseY, 0);
				} else {
					build3dScreenMenu();
				}
			}
		}
		if (anInt886 != anInt1026) {
			anInt1026 = anInt886;
		}
		if (anInt1315 != anInt1129) {
			anInt1129 = anInt1315;
		}
		anInt886 = 0;
		anInt1315 = 0;
		if (!changeTabArea) {
			final int yOffset = frameMode == ScreenMode.FIXED ? 0
					: frameHeight - 503;
			final int xOffset = frameMode == ScreenMode.FIXED ? 0
					: frameWidth - 765;
			if (super.mouseX > 548 + xOffset && super.mouseX < 740 + xOffset
					&& super.mouseY > 207 + yOffset
					&& super.mouseY < 468 + yOffset) {
				if (overlayInterfaceId != -1) {
					buildInterfaceMenu(548 + xOffset,
							Widget.interfaceCache[overlayInterfaceId],
							super.mouseX, 207 + yOffset, super.mouseY, 0);
				} else if (tabInterfaceIDs[tabID] != -1) {
					buildInterfaceMenu(548 + xOffset,
							Widget.interfaceCache[tabInterfaceIDs[tabID]],
							super.mouseX, 207 + yOffset, super.mouseY, 0);
				}
			}
		} else if (changeTabArea) {
			final int yOffset = frameWidth >= 1000 ? 37 : 74;
			if (super.mouseX > frameWidth - 197
					&& super.mouseY > frameHeight - yOffset - 267
					&& super.mouseX < frameWidth - 7
					&& super.mouseY < frameHeight - yOffset - 7
					&& showTabComponents) {
				if (overlayInterfaceId != -1) {
					buildInterfaceMenu(frameWidth - 197,
							Widget.interfaceCache[overlayInterfaceId],
							super.mouseX, frameHeight - yOffset - 267,
							super.mouseY, 0);
				} else if (tabInterfaceIDs[tabID] != -1) {
					buildInterfaceMenu(frameWidth - 197,
							Widget.interfaceCache[tabInterfaceIDs[tabID]],
							super.mouseX, frameHeight - yOffset - 267,
							super.mouseY, 0);
				}
			}
		}
		if (anInt886 != anInt1048) {
			tabAreaAltered = true;
			anInt1048 = anInt886;
		}
		if (anInt1315 != anInt1044) {
			tabAreaAltered = true;
			anInt1044 = anInt1315;
		}
		anInt886 = 0;
		anInt1315 = 0;
		if (super.mouseX > 0
				&& super.mouseY > (frameMode == ScreenMode.FIXED ? 338
						: frameHeight - 165)
				&& super.mouseX < 490
				&& super.mouseY < (frameMode == ScreenMode.FIXED ? 463
						: frameHeight - 40) && showChatComponents) {
			if (backDialogueId != -1) {
				buildInterfaceMenu(20, Widget.interfaceCache[backDialogueId],
						super.mouseX, (frameMode == ScreenMode.FIXED ? 358
								: frameHeight - 145), super.mouseY, 0);
			} else if (super.mouseY < (frameMode == ScreenMode.FIXED ? 463
					: frameHeight - 40) && super.mouseX < 490) {
				buildChatAreaMenu(super.mouseY
						- (frameMode == ScreenMode.FIXED ? 338
								: frameHeight - 165));
			}
		}
		if (backDialogueId != -1 && anInt886 != anInt1039) {
			inputTaken = true;
			anInt1039 = anInt886;
		}
		if (backDialogueId != -1 && anInt1315 != anInt1500) {
			inputTaken = true;
			anInt1500 = anInt1315;
		}
		if (super.mouseX > 4 && super.mouseY > 480 && super.mouseX < 516
				&& super.mouseY < frameHeight) {
			rightClickChatButtons();
		}
		processMinimapActions();
		boolean flag = false;
		while (!flag) {
			flag = true;
			for (int j = 0; j < menuActionRow - 1; j++) {
				if (menuActionID[j] < 1000 && menuActionID[j + 1] > 1000) {
					String s = menuActionName[j];
					menuActionName[j] = menuActionName[j + 1];
					menuActionName[j + 1] = s;
					int k = menuActionID[j];
					menuActionID[j] = menuActionID[j + 1];
					menuActionID[j + 1] = k;
					k = menuActionCmd2[j];
					menuActionCmd2[j] = menuActionCmd2[j + 1];
					menuActionCmd2[j + 1] = k;
					k = menuActionCmd3[j];
					menuActionCmd3[j] = menuActionCmd3[j + 1];
					menuActionCmd3[j + 1] = k;
					k = menuActionCmd1[j];
					menuActionCmd1[j] = menuActionCmd1[j + 1];
					menuActionCmd1[j + 1] = k;
					flag = false;
				}
			}
		}
	}

	private int method83(int i, int j, int k) {
		int l = 256 - k;
		return ((i & 0xff00ff) * l + (j & 0xff00ff) * k & 0xff00ff00)
				+ ((i & 0xff00) * l + (j & 0xff00) * k & 0xff0000) >> 8;
	}

	private void login(String name, String password, boolean reconnecting) {
		Signlink.setError(name);
		try {
			if (!reconnecting) {
				firstLoginMessage = "";
				secondLoginMessage = "Connecting to server...";
				drawLoginScreen(true);
			}
			socketStream = new RSSocket(this,
					openSocket(Configuration.server_port + portOff));
			long encoded = TextClass.longForName(name);
			int nameHash = (int) (encoded >> 16 & 31L);
			outgoing.currentPosition = 0;
			outgoing.writeByte(14);
			outgoing.writeByte(nameHash);
			socketStream.queueBytes(2, outgoing.payload);
			for (int j = 0; j < 8; j++)
				socketStream.read();

			int response = socketStream.read();
			int copy = response;
			if (response == 0) {
				socketStream.flushInputStream(incoming.payload, 8);
				incoming.currentPosition = 0;
				serverSeed = incoming.readLong();
				int seed[] = new int[4];
				seed[0] = (int) (Math.random() * 99999999D);
				seed[1] = (int) (Math.random() * 99999999D);
				seed[2] = (int) (serverSeed >> 32);
				seed[3] = (int) serverSeed;
				outgoing.currentPosition = 0;
				outgoing.writeByte(10);
				outgoing.writeInt(seed[0]);
				outgoing.writeInt(seed[1]);
				outgoing.writeInt(seed[2]);
				outgoing.writeInt(seed[3]);
				outgoing.writeInt(Signlink.uid);
				outgoing.writeString(UserIdentification.generateUID());
				outgoing.writeString(name);
				outgoing.writeString(password);
				outgoing.encodeRSA(NetworkConstants.RSA_EXPONENT,
						NetworkConstants.RSA_MODULUS);
				login.currentPosition = 0;
				if (reconnecting)
					login.writeByte(18);
				else
					login.writeByte(16);
				login.writeByte(outgoing.currentPosition + 36 + 1 + 1 + 2);
				login.writeByte(255); // magic number
				login.writeShort(317); // client version
				login.writeByte(lowMem ? 1 : 0);
				for (int index = 0; index < 9; index++)
					login.writeInt(archiveCRCs[index]);

				login.writeBytes(outgoing.payload, outgoing.currentPosition, 0);
				outgoing.encryption = new ISAACCipher(seed);
				for (int index = 0; index < 4; index++)
					seed[index] += 50;

				encryption = new ISAACCipher(seed);
				socketStream.queueBytes(login.currentPosition, login.payload);
				response = socketStream.read();
			}
			if (response == 1) {
				try {
					Thread.sleep(2000L);
				} catch (Exception _ex) {
				}
				login(name, password, reconnecting);
				return;
			}
			if (response == 2) {
				myPrivilege = socketStream.read();
				flagged = socketStream.read() == 1;
				aLong1220 = 0L;
				duplicateClickCount = 0;
				mouseDetection.coordsIndex = 0;
				super.awtFocus = true;
				aBoolean954 = true;
				loggedIn = true;
				outgoing.currentPosition = 0;
				incoming.currentPosition = 0;
				opCode = -1;
				lastOpcode = -1;
				secondLastOpcode = -1;
				thirdLastOpcode = -1;
				packetSize = 0;
				timeoutCounter = 0;
				systemUpdateTime = 0;
				anInt1011 = 0;
				hintIconDrawType = 0;
				menuActionRow = 0;
				menuOpen = false;
				super.idleTime = 0;
				for (int index = 0; index < 100; index++)
					chatMessages[index] = null;
				itemSelected = 0;
				spellSelected = 0;
				loadingStage = 0;
				trackCount = 0;
				setNorth();
				minimapState = 0;
				anInt985 = -1;
				destinationX = 0;
				destY = 0;
				playerCount = 0;
				npcCount = 0;
				for (int index = 0; index < maxPlayers; index++) {
					players[index] = null;
					playerSynchronizationBuffers[index] = null;
				}
				for (int index = 0; index < 16384; index++)
					npcs[index] = null;
				localPlayer = players[internalLocalPlayerIndex] = new Player();
				projectiles.clear();
				incompleteAnimables.clear();
				for (int z = 0; z < 4; z++) {
					for (int x = 0; x < 104; x++) {
						for (int y = 0; y < 104; y++)
							groundItems[z][x][y] = null;
					}
				}
				spawns = new Deque();
				fullscreenInterfaceID = -1;
				friendServerStatus = 0;
				friendsCount = 0;
				dialogueId = -1;
				backDialogueId = -1;
				openInterfaceId = -1;
				overlayInterfaceId = -1;
				openWalkableInterface = -1;
				continuedDialogue = false;
				tabID = 3;
				inputDialogState = 0;
				menuOpen = false;
				messagePromptRaised = false;
				clickToContinueString = null;
				multicombat = 0;
				flashingSidebarId = -1;
				maleCharacter = true;
				changeCharacterGender();
				for (int index = 0; index < 5; index++)
					characterDesignColours[index] = 0;
				for (int index = 0; index < 5; index++) {
					atPlayerActions[index] = null;
					atPlayerArray[index] = false;
				}
				anInt1175 = 0;
				anInt1134 = 0;
				anInt986 = 0;
				anInt1288 = 0;
				anInt924 = 0;
				anInt1188 = 0;
				anInt1155 = 0;
				anInt1226 = 0;
				sendConfiguration(429, 1);
				this.stopMidi();
				setupGameplayScreen();
				return;
			}
			if (response == 3) {
				firstLoginMessage = "";
				secondLoginMessage = "Invalid username or password.";
				return;
			}
			if (response == 4) {
				firstLoginMessage = "Your account has been disabled.";
				secondLoginMessage = "Please check your defaultText-center for details.";
				return;
			}
			if (response == 5) {
				firstLoginMessage = "Your account is already logged in.";
				secondLoginMessage = "Try again in 60 secs...";
				return;
			}
			if (response == 6) {
				firstLoginMessage = Configuration.CLIENT_NAME
						+ " has been updated!";
				secondLoginMessage = "Please reload this page.";
				return;
			}
			if (response == 7) {
				firstLoginMessage = "This world is full.";
				secondLoginMessage = "Please use a different world.";
				return;
			}
			if (response == 8) {
				firstLoginMessage = "Unable to connect.";
				secondLoginMessage = "Login server offline.";
				return;
			}
			if (response == 9) {
				firstLoginMessage = "Login limit exceeded.";
				secondLoginMessage = "Too many connections from your address.";
				return;
			}
			if (response == 10) {
				firstLoginMessage = "Unable to connect.";
				secondLoginMessage = "Bad session id.";
				return;
			}
			if (response == 11) {
				secondLoginMessage = "Login server rejected session.";
				secondLoginMessage = "Please try again.";
				return;
			}
			if (response == 12) {
				firstLoginMessage = "You need a members account to login to this world.";
				secondLoginMessage = "Please subscribe, or use a different world.";
				return;
			}
			if (response == 13) {
				firstLoginMessage = "Could not complete login.";
				secondLoginMessage = "Please try using a different world.";
				return;
			}
			if (response == 14) {
				firstLoginMessage = "The server is being updated.";
				secondLoginMessage = "Please wait 1 minute and try again.";
				return;
			}
			if (response == 15) {
				loggedIn = true;
				outgoing.currentPosition = 0;
				incoming.currentPosition = 0;
				opCode = -1;
				lastOpcode = -1;
				secondLastOpcode = -1;
				thirdLastOpcode = -1;
				packetSize = 0;
				timeoutCounter = 0;
				systemUpdateTime = 0;
				menuActionRow = 0;
				menuOpen = false;
				aLong824 = System.currentTimeMillis();
				return;
			}
			if (response == 16) {
				firstLoginMessage = "Login attempts exceeded.";
				secondLoginMessage = "Please wait 1 minute and try again.";
				return;
			}
			if (response == 17) {
				firstLoginMessage = "You are standing in a members-only area.";
				secondLoginMessage = "To play on this world move to a free area first";
				return;
			}
			if (response == 20) {
				firstLoginMessage = "Invalid loginserver requested";
				secondLoginMessage = "Please try using a different world.";
				return;
			}
			if (response == 21) {
				for (int k1 = socketStream.read(); k1 >= 0; k1--) {
					firstLoginMessage = "You have only just left another world";
					secondLoginMessage = "Your profile will be transferred in: "
							+ k1 + " seconds";
					drawLoginScreen(true);
					try {
						Thread.sleep(1000L);
					} catch (Exception _ex) {
					}
				}
				login(name, password, reconnecting);
				return;
			}
			if (response == 22) {
				firstLoginMessage = "Your computer has been UUID banned.";
				secondLoginMessage = "Please appeal on the forums.";
				return;
			}
			if (response == -1) {
				if (copy == 0) {
					if (loginFailures < 2) {
						try {
							Thread.sleep(2000L);
						} catch (Exception _ex) {
						}
						loginFailures++;
						login(name, password, reconnecting);
						return;
					} else {
						firstLoginMessage = "No response from loginserver";
						secondLoginMessage = "Please wait 1 minute and try again.";
						return;
					}
				} else {
					firstLoginMessage = "No response from server";
					secondLoginMessage = "Please try using a different world.";
					return;
				}
			} else {
				System.out.println("response:" + response);
				firstLoginMessage = "Unexpected server response";
				secondLoginMessage = "Please try using a different world.";
				return;
			}
		} catch (IOException _ex) {
			firstLoginMessage = "";
		} catch (Exception e) {
			System.out.println("Error while generating uid. Skipping step.");
			e.printStackTrace();
		}
		secondLoginMessage = "Error connecting to server.";
	}

	private boolean doWalkTo(int i, int j, int k, int i1, int j1, int k1,
			int l1, int i2, int j2, boolean flag, int k2) {
		byte byte0 = 104;
		byte byte1 = 104;
		for (int l2 = 0; l2 < byte0; l2++) {
			for (int i3 = 0; i3 < byte1; i3++) {
				anIntArrayArray901[l2][i3] = 0;
				anIntArrayArray825[l2][i3] = 0x5f5e0ff;
			}
		}
		int j3 = j2;
		int k3 = j1;
		anIntArrayArray901[j2][j1] = 99;
		anIntArrayArray825[j2][j1] = 0;
		int l3 = 0;
		int i4 = 0;
		bigX[l3] = j2;
		bigY[l3++] = j1;
		boolean flag1 = false;
		int j4 = bigX.length;
		int ai[][] = aClass11Array1230[plane].adjacencies;
		while (i4 != l3) {
			j3 = bigX[i4];
			k3 = bigY[i4];
			i4 = (i4 + 1) % j4;
			if (j3 == k2 && k3 == i2) {
				flag1 = true;
				break;
			}
			if (i1 != 0) {
				if ((i1 < 5 || i1 == 10)
						&& aClass11Array1230[plane].method219(k2, j3, k3, j,
								i1 - 1, i2)) {
					flag1 = true;
					break;
				}
				if (i1 < 10
						&& aClass11Array1230[plane].method220(k2, i2, k3,
								i1 - 1, j, j3)) {
					flag1 = true;
					break;
				}
			}
			if (k1 != 0
					&& k != 0
					&& aClass11Array1230[plane].method221(i2, k2, j3, k, l1,
							k1, k3)) {
				flag1 = true;
				break;
			}
			int l4 = anIntArrayArray825[j3][k3] + 1;
			if (j3 > 0 && anIntArrayArray901[j3 - 1][k3] == 0
					&& (ai[j3 - 1][k3] & 0x1280108) == 0) {
				bigX[l3] = j3 - 1;
				bigY[l3] = k3;
				l3 = (l3 + 1) % j4;
				anIntArrayArray901[j3 - 1][k3] = 2;
				anIntArrayArray825[j3 - 1][k3] = l4;
			}
			if (j3 < byte0 - 1 && anIntArrayArray901[j3 + 1][k3] == 0
					&& (ai[j3 + 1][k3] & 0x1280180) == 0) {
				bigX[l3] = j3 + 1;
				bigY[l3] = k3;
				l3 = (l3 + 1) % j4;
				anIntArrayArray901[j3 + 1][k3] = 8;
				anIntArrayArray825[j3 + 1][k3] = l4;
			}
			if (k3 > 0 && anIntArrayArray901[j3][k3 - 1] == 0
					&& (ai[j3][k3 - 1] & 0x1280102) == 0) {
				bigX[l3] = j3;
				bigY[l3] = k3 - 1;
				l3 = (l3 + 1) % j4;
				anIntArrayArray901[j3][k3 - 1] = 1;
				anIntArrayArray825[j3][k3 - 1] = l4;
			}
			if (k3 < byte1 - 1 && anIntArrayArray901[j3][k3 + 1] == 0
					&& (ai[j3][k3 + 1] & 0x1280120) == 0) {
				bigX[l3] = j3;
				bigY[l3] = k3 + 1;
				l3 = (l3 + 1) % j4;
				anIntArrayArray901[j3][k3 + 1] = 4;
				anIntArrayArray825[j3][k3 + 1] = l4;
			}
			if (j3 > 0 && k3 > 0 && anIntArrayArray901[j3 - 1][k3 - 1] == 0
					&& (ai[j3 - 1][k3 - 1] & 0x128010e) == 0
					&& (ai[j3 - 1][k3] & 0x1280108) == 0
					&& (ai[j3][k3 - 1] & 0x1280102) == 0) {
				bigX[l3] = j3 - 1;
				bigY[l3] = k3 - 1;
				l3 = (l3 + 1) % j4;
				anIntArrayArray901[j3 - 1][k3 - 1] = 3;
				anIntArrayArray825[j3 - 1][k3 - 1] = l4;
			}
			if (j3 < byte0 - 1 && k3 > 0
					&& anIntArrayArray901[j3 + 1][k3 - 1] == 0
					&& (ai[j3 + 1][k3 - 1] & 0x1280183) == 0
					&& (ai[j3 + 1][k3] & 0x1280180) == 0
					&& (ai[j3][k3 - 1] & 0x1280102) == 0) {
				bigX[l3] = j3 + 1;
				bigY[l3] = k3 - 1;
				l3 = (l3 + 1) % j4;
				anIntArrayArray901[j3 + 1][k3 - 1] = 9;
				anIntArrayArray825[j3 + 1][k3 - 1] = l4;
			}
			if (j3 > 0 && k3 < byte1 - 1
					&& anIntArrayArray901[j3 - 1][k3 + 1] == 0
					&& (ai[j3 - 1][k3 + 1] & 0x1280138) == 0
					&& (ai[j3 - 1][k3] & 0x1280108) == 0
					&& (ai[j3][k3 + 1] & 0x1280120) == 0) {
				bigX[l3] = j3 - 1;
				bigY[l3] = k3 + 1;
				l3 = (l3 + 1) % j4;
				anIntArrayArray901[j3 - 1][k3 + 1] = 6;
				anIntArrayArray825[j3 - 1][k3 + 1] = l4;
			}
			if (j3 < byte0 - 1 && k3 < byte1 - 1
					&& anIntArrayArray901[j3 + 1][k3 + 1] == 0
					&& (ai[j3 + 1][k3 + 1] & 0x12801e0) == 0
					&& (ai[j3 + 1][k3] & 0x1280180) == 0
					&& (ai[j3][k3 + 1] & 0x1280120) == 0) {
				bigX[l3] = j3 + 1;
				bigY[l3] = k3 + 1;
				l3 = (l3 + 1) % j4;
				anIntArrayArray901[j3 + 1][k3 + 1] = 12;
				anIntArrayArray825[j3 + 1][k3 + 1] = l4;
			}
		}
		anInt1264 = 0;
		if (!flag1) {
			if (flag) {
				int i5 = 100;
				for (int k5 = 1; k5 < 2; k5++) {
					for (int i6 = k2 - k5; i6 <= k2 + k5; i6++) {
						for (int l6 = i2 - k5; l6 <= i2 + k5; l6++) {
							if (i6 >= 0 && l6 >= 0 && i6 < 104 && l6 < 104
									&& anIntArrayArray825[i6][l6] < i5) {
								i5 = anIntArrayArray825[i6][l6];
								j3 = i6;
								k3 = l6;
								anInt1264 = 1;
								flag1 = true;
							}
						}
					}
					if (flag1)
						break;
				}
			}
			if (!flag1)
				return false;
		}
		i4 = 0;
		bigX[i4] = j3;
		bigY[i4++] = k3;
		int l5;
		for (int j5 = l5 = anIntArrayArray901[j3][k3]; j3 != j2 || k3 != j1; j5 = anIntArrayArray901[j3][k3]) {
			if (j5 != l5) {
				l5 = j5;
				bigX[i4] = j3;
				bigY[i4++] = k3;
			}
			if ((j5 & 2) != 0)
				j3++;
			else if ((j5 & 8) != 0)
				j3--;
			if ((j5 & 1) != 0)
				k3++;
			else if ((j5 & 4) != 0)
				k3--;
		}
		if (i4 > 0) {
			int k4 = i4;
			if (k4 > 25)
				k4 = 25;
			i4--;
			int k6 = bigX[i4];
			int i7 = bigY[i4];
			anInt1288 += k4;
			if (anInt1288 >= 92) {
				outgoing.createFrame(36);
				outgoing.writeInt(0);
				anInt1288 = 0;
			}
			if (i == 0) {
				outgoing.createFrame(164);
				outgoing.writeByte(k4 + k4 + 3);
			}
			if (i == 1) {
				outgoing.createFrame(248);
				outgoing.writeByte(k4 + k4 + 3 + 14);
			}
			if (i == 2) {
				outgoing.createFrame(98);
				outgoing.writeByte(k4 + k4 + 3);
			}
			outgoing.writeLEShortA(k6 + baseX);
			destinationX = bigX[0];
			destY = bigY[0];
			for (int j7 = 1; j7 < k4; j7++) {
				i4--;
				outgoing.writeByte(bigX[i4] - k6);
				outgoing.writeByte(bigY[i4] - i7);
			}
			outgoing.writeLEShort(i7 + baseY);
			outgoing.writeNegatedByte(super.keyArray[5] != 1 ? 0 : 1);
			return true;
		}
		return i != 1;
	}

	private void npcUpdateMask(Buffer stream) {
		for (int j = 0; j < anInt893; j++) {
			int k = anIntArray894[j];
			Npc npc = npcs[k];
			int l = stream.readUnsignedByte();
			if ((l & 0x10) != 0) {
				int i1 = stream.readLEUShort();
				if (i1 == 65535)
					i1 = -1;
				int i2 = stream.readUnsignedByte();
				if (i1 == npc.emoteAnimation && i1 != -1) {
					int l2 = Animation.animations[i1].anInt365;
					if (l2 == 1) {
						npc.anInt1527 = 0;
						npc.anInt1528 = 0;
						npc.anInt1529 = i2;
						npc.anInt1530 = 0;
					}
					if (l2 == 2)
						npc.anInt1530 = 0;
				} else if (i1 == -1
						|| npc.emoteAnimation == -1
						|| Animation.animations[i1].anInt359 >= Animation.animations[npc.emoteAnimation].anInt359) {
					npc.emoteAnimation = i1;
					npc.anInt1527 = 0;
					npc.anInt1528 = 0;
					npc.anInt1529 = i2;
					npc.anInt1530 = 0;
					npc.anInt1542 = npc.smallXYIndex;
				}
			}
			if ((l & 8) != 0) {
				int j1 = stream.readUByteA();
				int j2 = stream.readNegUByte();
				npc.updateHitData(j2, j1, loopCycle);
				npc.loopCycleStatus = loopCycle + 300;
				npc.currentHealth = stream.readUByteA();
				npc.maxHealth = stream.readUnsignedByte();
			}
			if ((l & 0x80) != 0) {
				npc.gfxId = stream.readUShort();
				int k1 = stream.readInt();
				npc.anInt1524 = k1 >> 16;
				npc.anInt1523 = loopCycle + (k1 & 0xffff);
				npc.anInt1521 = 0;
				npc.anInt1522 = 0;
				if (npc.anInt1523 > loopCycle)
					npc.anInt1521 = -1;
				if (npc.gfxId == 65535)
					npc.gfxId = -1;
			}
			if ((l & 0x20) != 0) {
				npc.interactingEntity = stream.readUShort();
				if (npc.interactingEntity == 65535)
					npc.interactingEntity = -1;
			}
			if ((l & 1) != 0) {
				npc.spokenText = stream.readString();
				npc.textCycle = 100;
			}
			if ((l & 0x40) != 0) {
				int l1 = stream.readNegUByte();
				int k2 = stream.readUByteS();
				npc.updateHitData(k2, l1, loopCycle);
				npc.loopCycleStatus = loopCycle + 300;
				npc.currentHealth = stream.readUByteS();
				npc.maxHealth = stream.readNegUByte();
			}
			if ((l & 2) != 0) {
				npc.desc = NpcDefinition.lookup(stream.readLEUShortA());
				npc.boundDim = npc.desc.boundDim;
				npc.degreesToTurn = npc.desc.degreesToTurn;
				npc.walkAnimIndex = npc.desc.walkAnim;
				npc.turn180AnimIndex = npc.desc.turn180AnimIndex;
				npc.turn90CWAnimIndex = npc.desc.turn90CWAnimIndex;
				npc.turn90CCWAnimIndex = npc.desc.turn90CCWAnimIndex;
				npc.standAnimIndex = npc.desc.standAnim;
			}
			if ((l & 4) != 0) {
				npc.anInt1538 = stream.readLEUShort();
				npc.anInt1539 = stream.readLEUShort();
			}
		}
	}

	private void buildAtNPCMenu(NpcDefinition entityDef, int i, int j, int k) {
		if (menuActionRow >= 400)
			return;
		if (entityDef.childrenIDs != null)
			entityDef = entityDef.morph();
		if (entityDef == null)
			return;
		if (!entityDef.clickable)
			return;
		String s = entityDef.name;
		
		if (entityDef.combatLevel != 0)
			s = s
					+ combatDiffColor(localPlayer.combatLevel,
							entityDef.combatLevel) + " (level-"
					+ entityDef.combatLevel + ")";
		if (itemSelected == 1) {
			menuActionName[menuActionRow] = "Use " + selectedItemName
					+ " with @yel@" + s;
			menuActionID[menuActionRow] = 582;
			menuActionCmd1[menuActionRow] = i;
			menuActionCmd2[menuActionRow] = k;
			menuActionCmd3[menuActionRow] = j;
			menuActionRow++;
			return;
		}
		if (spellSelected == 1) {
			if ((spellUsableOn & 2) == 2) {
				menuActionName[menuActionRow] = spellTooltip + " @yel@" + s;
				menuActionID[menuActionRow] = 413;
				menuActionCmd1[menuActionRow] = i;
				menuActionCmd2[menuActionRow] = k;
				menuActionCmd3[menuActionRow] = j;
				menuActionRow++;
			}
		} else {
			if (entityDef.actions != null) {
				for (int l = 4; l >= 0; l--)
					if (entityDef.actions[l] != null
							&& !entityDef.actions[l].equalsIgnoreCase("attack")) {
						menuActionName[menuActionRow] = entityDef.actions[l]
								+ " @yel@" + s;
						if (l == 0)
							menuActionID[menuActionRow] = 20;
						if (l == 1)
							menuActionID[menuActionRow] = 412;
						if (l == 2)
							menuActionID[menuActionRow] = 225;
						if (l == 3)
							menuActionID[menuActionRow] = 965;
						if (l == 4)
							menuActionID[menuActionRow] = 478;
						menuActionCmd1[menuActionRow] = i;
						menuActionCmd2[menuActionRow] = k;
						menuActionCmd3[menuActionRow] = j;
						menuActionRow++;
					}

			}
			if (entityDef.actions != null) {
				for (int i1 = 4; i1 >= 0; i1--)
					if (entityDef.actions[i1] != null
							&& entityDef.actions[i1].equalsIgnoreCase("attack")) {
						char c = '\0';
						if (entityDef.combatLevel > localPlayer.combatLevel)
							c = '\u07D0';
						menuActionName[menuActionRow] = entityDef.actions[i1]
								+ " @yel@" + s;
						if (i1 == 0)
							menuActionID[menuActionRow] = 20 + c;
						if (i1 == 1)
							menuActionID[menuActionRow] = 412 + c;
						if (i1 == 2)
							menuActionID[menuActionRow] = 225 + c;
						if (i1 == 3)
							menuActionID[menuActionRow] = 965 + c;
						if (i1 == 4)
							menuActionID[menuActionRow] = 478 + c;
						menuActionCmd1[menuActionRow] = i;
						menuActionCmd2[menuActionRow] = k;
						menuActionCmd3[menuActionRow] = j;
						menuActionRow++;
					}

			}
			if (Configuration.enableIds && (myPrivilege >= 2 && myPrivilege <= 3)) {
				menuActionName[menuActionRow] = "Examine @yel@" + s
						+ " @gre@(@whi@" + entityDef.interfaceType + "@gre@)";
			} else {
				menuActionName[menuActionRow] = "Examine @yel@" + s;
			}
			menuActionID[menuActionRow] = 1025;
			menuActionCmd1[menuActionRow] = i;
			menuActionCmd2[menuActionRow] = k;
			menuActionCmd3[menuActionRow] = j;
			menuActionRow++;
		}
	}

	private void buildAtPlayerMenu(int i, int j, Player player, int k) {
		if (player == localPlayer)
			return;
		if (menuActionRow >= 400)
			return;
		String s;
		if (player.skill == 0)
			s = player.name
					+ combatDiffColor(localPlayer.combatLevel,
							player.combatLevel) + " (level-"
					+ player.combatLevel + ")";
		else
			s = player.name + " (skill-" + player.skill + ")";
		if (itemSelected == 1) {
			menuActionName[menuActionRow] = "Use " + selectedItemName
					+ " with @whi@" + s;
			menuActionID[menuActionRow] = 491;
			menuActionCmd1[menuActionRow] = j;
			menuActionCmd2[menuActionRow] = i;
			menuActionCmd3[menuActionRow] = k;
			menuActionRow++;
		} else if (spellSelected == 1) {
			if ((spellUsableOn & 8) == 8) {
				menuActionName[menuActionRow] = spellTooltip + " @whi@" + s;
				menuActionID[menuActionRow] = 365;
				menuActionCmd1[menuActionRow] = j;
				menuActionCmd2[menuActionRow] = i;
				menuActionCmd3[menuActionRow] = k;
				menuActionRow++;
			}
		} else {
			for (int l = 4; l >= 0; l--)
				if (atPlayerActions[l] != null) {
					menuActionName[menuActionRow] = atPlayerActions[l]
							+ " @whi@" + s;
					char c = '\0';
					if (atPlayerActions[l].equalsIgnoreCase("attack")) {
						if (player.combatLevel > localPlayer.combatLevel)
							c = '\u07D0';
						if (localPlayer.team != 0 && player.team != 0)
							if (localPlayer.team == player.team)
								c = '\u07D0';
							else
								c = '\0';
					} else if (atPlayerArray[l])
						c = '\u07D0';
					if (l == 0)
						menuActionID[menuActionRow] = 561 + c;
					if (l == 1)
						menuActionID[menuActionRow] = 779 + c;
					if (l == 2)
						menuActionID[menuActionRow] = 27 + c;
					if (l == 3)
						menuActionID[menuActionRow] = 577 + c;
					if (l == 4)
						menuActionID[menuActionRow] = 729 + c;
					menuActionCmd1[menuActionRow] = j;
					menuActionCmd2[menuActionRow] = i;
					menuActionCmd3[menuActionRow] = k;
					menuActionRow++;
				}
		}
		for (int i1 = 0; i1 < menuActionRow; i1++) {
			if (menuActionID[i1] == 519) {			
				menuActionName[i1] = "Walk here @whi@" + s;
				return;
			}
		}
	}

	private void method89(TemporaryObject class30_sub1) {
		int i = 0;
		int j = -1;
		int k = 0;
		int l = 0;
		if (class30_sub1.anInt1296 == 0)
			i = worldController.method300(class30_sub1.anInt1295,
					class30_sub1.anInt1297, class30_sub1.anInt1298);
		if (class30_sub1.anInt1296 == 1)
			i = worldController.method301(class30_sub1.anInt1295,
					class30_sub1.anInt1297, class30_sub1.anInt1298);
		if (class30_sub1.anInt1296 == 2)
			i = worldController.method302(class30_sub1.anInt1295,
					class30_sub1.anInt1297, class30_sub1.anInt1298);
		if (class30_sub1.anInt1296 == 3)
			i = worldController.method303(class30_sub1.anInt1295,
					class30_sub1.anInt1297, class30_sub1.anInt1298);
		if (i != 0) {
			int i1 = worldController.method304(class30_sub1.anInt1295,
					class30_sub1.anInt1297, class30_sub1.anInt1298, i);
			j = i >> 14 & 0x7fff;
			k = i1 & 0x1f;
			l = i1 >> 6;
		}
		class30_sub1.anInt1299 = j;
		class30_sub1.anInt1301 = k;
		class30_sub1.anInt1300 = l;
	}

	void startUp() {
		drawLoadingText(20, "Starting up");
		if (Signlink.cache_dat != null) {
			for (int i = 0; i < 5; i++)
				decompressors[i] = new Index(Signlink.cache_dat,
						Signlink.cache_idx[i], i + 1);
		}
		try {
			titleStreamLoader = streamLoaderForName(1, "title screen", "title",
					archiveCRCs[1], 25);
			smallText = new GameFont(false, "p11_full", titleStreamLoader);
			regularText = new GameFont(false, "p12_full", titleStreamLoader);
			boldText = new GameFont(false, "b12_full", titleStreamLoader);
			newSmallFont = new RSFont(false, "p11_full", titleStreamLoader);
			newRegularFont = new RSFont(false, "p12_full", titleStreamLoader);
			newBoldFont = new RSFont(false, "b12_full", titleStreamLoader);
			newFancyFont = new RSFont(true, "q8_full", titleStreamLoader);
			GameFont aTextDrawingArea_1273 = new GameFont(true, "q8_full",
					titleStreamLoader);
			drawLogo();
			loadTitleScreen();
			CacheArchive streamLoader = streamLoaderForName(2, "config",
					"config", archiveCRCs[2], 30);
			CacheArchive streamLoader_1 = streamLoaderForName(3, "interface",
					"interface", archiveCRCs[3], 35);
			CacheArchive streamLoader_2 = streamLoaderForName(4, "2d graphics",
					"media", archiveCRCs[4], 40);
			this.mediaStreamLoader = streamLoader_2;
			CacheArchive streamLoader_3 = streamLoaderForName(6, "textures",
					"textures", archiveCRCs[6], 45);
			CacheArchive streamLoader_4 = streamLoaderForName(7, "chat system",
					"wordenc", archiveCRCs[7], 50);
			streamLoaderForName(8, "sound effects", "sounds", archiveCRCs[8],
					55);
			byteGroundArray = new byte[4][104][104];
			intGroundArray = new int[4][105][105];
			worldController = new SceneGraph(intGroundArray);
			for (int j = 0; j < 4; j++)
				aClass11Array1230[j] = new CollisionMap();

			minimapImage = new Sprite(512, 512);
			CacheArchive streamLoader_6 = streamLoaderForName(5, "update list",
					"versionlist", archiveCRCs[5], 60);
			drawLoadingText(60, "Connecting to update server");
			onDemandFetcher = new OnDemandRequester();
			onDemandFetcher.start(streamLoader_6, this);
			Model.method459(onDemandFetcher.getModelCount(), onDemandFetcher);
			drawLoadingText(80, "Unpacking media");
			CacheArchive streamLoader_5 = streamLoaderForName(8,
					"sound effects", "sounds", archiveCRCs[8], 55);
			byte abyte0[] = streamLoader_5.getDataForName("sounds.dat");

			Buffer stream = new Buffer(abyte0);
			SoundTrack.unpack(stream);

			if (Configuration.repackIndexOne) {
				repackCacheIndex(1);
			}
			if (Configuration.repackIndexTwo) {
				repackCacheIndex(2);
			}
			if (Configuration.repackIndexThree) {
				repackCacheIndex(3);
			}
			if (Configuration.repackIndexFour) {
				repackCacheIndex(4);
			}
			if (Configuration.dumpIndexOne) {
				dumpCacheIndex(1);
			}
			if (Configuration.dumpIndexTwo) {
				dumpCacheIndex(2);
			}
			if (Configuration.dumpIndexThree) {
				dumpCacheIndex(3);
			}
			if (Configuration.repackIndexFour) {
				dumpCacheIndex(4);
			}
			// GetMusic(3); //Used to pack new songs (midi format)
			// musics();

			File[] file = new File(Signlink.findcachedir()
					+ "/Sprites/Sprites/").listFiles();
			int size = file.length;
			cacheSprite = new Sprite[size];
			System.out.println("Images Loaded: " + size);
			for (int i = 0; i < size; i++) {
				cacheSprite[i] = new Sprite("Sprites/" + i);
			}
			for (int imageId = 0; imageId < SkillConstants.skillsCount; imageId++) {
				skill_sprites[imageId] = new Sprite("xp_drop/" + imageId);
			}
			multiOverlay = new Sprite(streamLoader_2, "overlay_multiway", 0);
			mapBack = new Background(streamLoader_2, "mapback", 0);
			for (int j3 = 0; j3 <= 14; j3++)
				sideIcons[j3] = new Sprite(streamLoader_2, "sideicons", j3);
			compass = new Sprite(streamLoader_2, "compass", 0);
			try {
				for (int k3 = 0; k3 < 100; k3++)
					mapScenes[k3] = new Background(streamLoader_2, "mapscene",
							k3);
			} catch (Exception _ex) {
			}
			try {
				for (int l3 = 0; l3 < 100; l3++)
					mapFunctions[l3] = new Sprite(streamLoader_2,
							"mapfunction", l3);
			} catch (Exception _ex) {
			}
			try {
				for (int i4 = 0; i4 < 20; i4++)
					hitMarks[i4] = new Sprite(streamLoader_2, "hitmarks", i4);
			} catch (Exception _ex) {
			}
			try {
				for (int h1 = 0; h1 < 6; h1++)
					headIconsHint[h1] = new Sprite(streamLoader_2,
							"headicons_hint", h1);
			} catch (Exception _ex) {
			}
			try {
				for (int j4 = 0; j4 < 8; j4++)
					headIcons[j4] = new Sprite(streamLoader_2,
							"headicons_prayer", j4);
				for (int j45 = 0; j45 < 3; j45++)
					skullIcons[j45] = new Sprite(streamLoader_2,
							"headicons_pk", j45);
			} catch (Exception _ex) {
			}
			mapFlag = new Sprite(streamLoader_2, "mapmarker", 0);
			mapMarker = new Sprite(streamLoader_2, "mapmarker", 1);
			for (int k4 = 0; k4 < 8; k4++)
				crosses[k4] = new Sprite(streamLoader_2, "cross", k4);
			mapDotItem = new Sprite(streamLoader_2, "mapdots", 0);
			mapDotNPC = new Sprite(streamLoader_2, "mapdots", 1);
			mapDotPlayer = new Sprite(streamLoader_2, "mapdots", 2);
			mapDotFriend = new Sprite(streamLoader_2, "mapdots", 3);
			mapDotTeam = new Sprite(streamLoader_2, "mapdots", 4);
			mapDotClan = new Sprite(streamLoader_2, "mapdots", 5);
			scrollBar1 = new Sprite(streamLoader_2, "scrollbar", 0);
			scrollBar2 = new Sprite(streamLoader_2, "scrollbar", 1);
			for (int l4 = 0; l4 < 2; l4++)
				modIcons[l4] = new Sprite(streamLoader_2, "mod_icons", l4);
			Sprite sprite = new Sprite(streamLoader_2, "screenframe", 0);
			leftFrame = new ImageProducer(sprite.myWidth, sprite.myHeight);
			sprite.method346(0, 0);
			sprite = new Sprite(streamLoader_2, "screenframe", 1);
			topFrame = new ImageProducer(sprite.myWidth, sprite.myHeight);
			sprite.method346(0, 0);
			int i5 = (int) (Math.random() * 21D) - 10;
			int j5 = (int) (Math.random() * 21D) - 10;
			int k5 = (int) (Math.random() * 21D) - 10;
			int l5 = (int) (Math.random() * 41D) - 20;
			for (int i6 = 0; i6 < 100; i6++) {
				if (mapFunctions[i6] != null)
					mapFunctions[i6].method344(i5 + l5, j5 + l5, k5 + l5);
				if (mapScenes[i6] != null)
					mapScenes[i6].method360(i5 + l5, j5 + l5, k5 + l5);
			}
			drawLoadingText(83, "Unpacking textures");
			Rasterizer.method368(streamLoader_3);
			Rasterizer.method372(0.80000000000000004D);
			Rasterizer.method367();
			drawLoadingText(86, "Unpacking config");
			Animation.unpackConfig(streamLoader);
			ObjectDefinition.unpackConfig(streamLoader);
			Floor.unpackConfig(streamLoader);
			ItemDefinition.unpackConfig(streamLoader);
			NpcDefinition.unpackConfig(streamLoader);
			IdentityKit.unpackConfig(streamLoader);
			SpotAnimation.unpackConfig(streamLoader);
			VariableParameter.unpackConfig(streamLoader);
			VariableBits.unpackConfig(streamLoader);
			ItemDefinition.isMembers = isMembers;
			drawLoadingText(95, "Unpacking interfaces");
			GameFont aclass30_sub2_sub1_sub4s[] = { smallText, regularText,
					boldText, aTextDrawingArea_1273 };
			Widget.load(streamLoader_1, aclass30_sub2_sub1_sub4s,
					streamLoader_2);
			drawLoadingText(100, "Preparing game engine");
			for (int j6 = 0; j6 < 33; j6++) {
				int k6 = 999;
				int i7 = 0;
				for (int k7 = 0; k7 < 34; k7++) {
					if (mapBack.aByteArray1450[k7 + j6 * mapBack.anInt1452] == 0) {
						if (k6 == 999)
							k6 = k7;
						continue;
					}
					if (k6 == 999)
						continue;
					i7 = k7;
					break;
				}
				anIntArray968[j6] = k6;
				anIntArray1057[j6] = i7 - k6;
			}
			for (int l6 = 1; l6 < 153; l6++) {
				int j7 = 999;
				int l7 = 0;
				for (int j8 = 24; j8 < 177; j8++) {
					if (mapBack.aByteArray1450[j8 + l6 * mapBack.anInt1452] == 0
							&& (j8 > 34 || l6 > 34)) {
						if (j7 == 999) {
							j7 = j8;
						}
						continue;
					}
					if (j7 == 999) {
						continue;
					}
					l7 = j8;
					break;
				}
				minimapLeft[l6 - 1] = j7 - 24;
				minimapLineWidth[l6 - 1] = l7 - j7;
			}
			setBounds();
			MessageCensor.load(streamLoader_4);
			mouseDetection = new MouseDetection(this);
			startRunnable(mouseDetection, 10);
			SceneObject.clientInstance = this;
			ObjectDefinition.clientInstance = this;
			NpcDefinition.clientInstance = this;
			return;
		} catch (Exception exception) {
			exception.printStackTrace();
			Signlink.reporterror("loaderror " + aString1049 + " " + anInt1079);
		}
		loadingError = true;
	}

	private void updateOtherPlayerMovement(Buffer stream, int i) {
		while (stream.bitPosition + 10 < i * 8) {
			int j = stream.readBits(11);
			if (j == 2047)
				break;
			if (players[j] == null) {
				players[j] = new Player();
				if (playerSynchronizationBuffers[j] != null)
					players[j].updatePlayer(playerSynchronizationBuffers[j]);
			}
			playerIndices[playerCount++] = j;
			Player player = players[j];
			player.anInt1537 = loopCycle;
			int k = stream.readBits(1);
			if (k == 1)
				anIntArray894[anInt893++] = j;
			int l = stream.readBits(1);
			int i1 = stream.readBits(5);
			if (i1 > 15)
				i1 -= 32;
			int j1 = stream.readBits(5);
			if (j1 > 15)
				j1 -= 32;
			player.setPos(localPlayer.pathX[0] + j1, localPlayer.pathY[0] + i1,
					l == 1);
		}
		stream.finishBitAccess();
	}

	public byte[] fileToByteArray(int cacheIndex, int index) {
		try {
			if (indexLocation(cacheIndex, index).length() <= 0
					|| indexLocation(cacheIndex, index) == null) {
				return null;
			}
			File file = new File(indexLocation(cacheIndex, index));
			byte[] fileData = new byte[(int) file.length()];
			FileInputStream fis = new FileInputStream(file);
			fis.read(fileData);
			fis.close();
			return fileData;
		} catch (Exception e) {
			return null;
		}
	}

	public boolean inCircle(int circleX, int circleY, int clickX, int clickY,
			int radius) {
		return java.lang.Math.pow((circleX + radius - clickX), 2)
				+ java.lang.Math.pow((circleY + radius - clickY), 2) < java.lang.Math
					.pow(radius, 2);
	}

	private void processMainScreenClick() {
		if (minimapState != 0)
			return;
		if (super.clickMode3 == 1) {
			int i = super.saveClickX - 25 - 547;
			int j = super.saveClickY - 5 - 3;
			if (frameMode != ScreenMode.FIXED) {
				i = super.saveClickX - (frameWidth - 182 + 24);
				j = super.saveClickY - 8;
			}
			if (inCircle(0, 0, i, j, 76) && mouseMapPosition() && !runHover) {
				i -= 73;
				j -= 75;
				int k = cameraHorizontal + minimapRotation & 0x7ff;
				int i1 = Rasterizer.anIntArray1470[k];
				int j1 = Rasterizer.anIntArray1471[k];
				i1 = i1 * (minimapZoom + 256) >> 8;
				j1 = j1 * (minimapZoom + 256) >> 8;
				int k1 = j * i1 + i * j1 >> 11;
				int l1 = j * j1 - i * i1 >> 11;
				int i2 = localPlayer.x + k1 >> 7;
				int j2 = localPlayer.y - l1 >> 7;
				boolean flag1 = doWalkTo(1, 0, 0, 0, localPlayer.pathY[0], 0,
						0, j2, localPlayer.pathX[0], true, i2);
				if (flag1) {
					outgoing.writeByte(i);
					outgoing.writeByte(j);
					outgoing.writeShort(cameraHorizontal);
					outgoing.writeByte(57);
					outgoing.writeByte(minimapRotation);
					outgoing.writeByte(minimapZoom);
					outgoing.writeByte(89);
					outgoing.writeShort(localPlayer.x);
					outgoing.writeShort(localPlayer.y);
					outgoing.writeByte(anInt1264);
					outgoing.writeByte(63);
				}
			}
			anInt1117++;
			if (anInt1117 > 1151) {
				anInt1117 = 0;
				outgoing.createFrame(246);
				outgoing.writeByte(0);
				int l = outgoing.currentPosition;
				if ((int) (Math.random() * 2D) == 0)
					outgoing.writeByte(101);
				outgoing.writeByte(197);
				outgoing.writeShort((int) (Math.random() * 65536D));
				outgoing.writeByte((int) (Math.random() * 256D));
				outgoing.writeByte(67);
				outgoing.writeShort(14214);
				if ((int) (Math.random() * 2D) == 0)
					outgoing.writeShort(29487);
				outgoing.writeShort((int) (Math.random() * 65536D));
				if ((int) (Math.random() * 2D) == 0)
					outgoing.writeByte(220);
				outgoing.writeByte(180);
				outgoing.writeBytes(outgoing.currentPosition - l);
			}
		}
	}

	private String interfaceIntToString(int j) {
		if (j < 0x3b9ac9ff)
			return String.valueOf(j);
		else
			return "*";
	}

	private void showErrorScreen() {
		Graphics g = getGameComponent().getGraphics();
		g.setColor(Color.black);
		g.fillRect(0, 0, 765, 503);
		method4(1);
		if (loadingError) {
			aBoolean831 = false;
			g.setFont(new Font("Helvetica", 1, 16));
			g.setColor(Color.yellow);
			int k = 35;
			g.drawString("Sorry, an error has occured whilst loading "
					+ Configuration.CLIENT_NAME, 30, k);
			k += 50;
			g.setColor(Color.white);
			g.drawString("To fix this try the following (in order):", 30, k);
			k += 50;
			g.setColor(Color.white);
			g.setFont(new Font("Helvetica", 1, 12));
			g.drawString(
					"1: Try closing ALL open web-browser windows, and reloading",
					30, k);
			k += 30;
			g.drawString(
					"2: Try clearing your web-browsers cache from tools->internet options",
					30, k);
			k += 30;
			g.drawString("3: Try using a different game-world", 30, k);
			k += 30;
			g.drawString("4: Try rebooting your computer", 30, k);
			k += 30;
			g.drawString(
					"5: Try selecting a different version of Java from the play-game menu",
					30, k);
		}
		if (genericLoadingError) {
			aBoolean831 = false;
			g.setFont(new Font("Helvetica", 1, 20));
			g.setColor(Color.white);
			g.drawString("Error - unable to load game!", 50, 50);
			g.drawString("To play " + Configuration.CLIENT_NAME
					+ " make sure you play from", 50, 100);
			g.drawString("http://www.UrlHere.com", 50, 150);
		}
		if (rsAlreadyLoaded) {
			aBoolean831 = false;
			g.setColor(Color.yellow);
			int l = 35;
			g.drawString("Error a copy of " + Configuration.CLIENT_NAME
					+ " already appears to be loaded", 30, l);
			l += 50;
			g.setColor(Color.white);
			g.drawString("To fix this try the following (in order):", 30, l);
			l += 50;
			g.setColor(Color.white);
			g.setFont(new Font("Helvetica", 1, 12));
			g.drawString(
					"1: Try closing ALL open web-browser windows, and reloading",
					30, l);
			l += 30;
			g.drawString("2: Try rebooting your computer, and reloading", 30, l);
			l += 30;
		}
	}

	public URL getCodeBase() {
		try {
			return new URL(server + ":" + (80 + portOff));
		} catch (Exception _ex) {
		}
		return null;
	}

	private void forceNPCUpdateBlock() {
		for (int j = 0; j < npcCount; j++) {
			int k = npcIndices[j];
			Npc npc = npcs[k];
			if (npc != null)
				entityUpdateBlock(npc);
		}
	}

	private void entityUpdateBlock(Entity entity) {
		if (entity.x < 128 || entity.y < 128 || entity.x >= 13184
				|| entity.y >= 13184) {
			entity.emoteAnimation = -1;
			entity.gfxId = -1;
			entity.anInt1547 = 0;
			entity.anInt1548 = 0;
			entity.x = entity.pathX[0] * 128 + entity.boundDim * 64;
			entity.y = entity.pathY[0] * 128 + entity.boundDim * 64;
			entity.resetPath();
		}
		if (entity == localPlayer
				&& (entity.x < 1536 || entity.y < 1536 || entity.x >= 11776 || entity.y >= 11776)) {
			entity.emoteAnimation = -1;
			entity.gfxId = -1;
			entity.anInt1547 = 0;
			entity.anInt1548 = 0;
			entity.x = entity.pathX[0] * 128 + entity.boundDim * 64;
			entity.y = entity.pathY[0] * 128 + entity.boundDim * 64;
			entity.resetPath();
		}
		if (entity.anInt1547 > loopCycle)
			refreshEntityPosition(entity);
		else if (entity.anInt1548 >= loopCycle)
			refreshEntityFaceDirection(entity);
		else
			getDegreesToTurn(entity);
		appendFocusDestination(entity);
		appendEmote(entity);
	}

	private void refreshEntityPosition(Entity entity) {
		int i = entity.anInt1547 - loopCycle;
		int j = entity.anInt1543 * 128 + entity.boundDim * 64;
		int k = entity.anInt1545 * 128 + entity.boundDim * 64;
		entity.x += (j - entity.x) / i;
		entity.y += (k - entity.y) / i;
		entity.anInt1503 = 0;
		if (entity.anInt1549 == 0)
			entity.turnDirection = 1024;
		if (entity.anInt1549 == 1)
			entity.turnDirection = 1536;
		if (entity.anInt1549 == 2)
			entity.turnDirection = 0;
		if (entity.anInt1549 == 3)
			entity.turnDirection = 512;
	}

	private void refreshEntityFaceDirection(Entity entity) {
		if (entity.anInt1548 == loopCycle
				|| entity.emoteAnimation == -1
				|| entity.anInt1529 != 0
				|| entity.anInt1528 + 1 > Animation.animations[entity.emoteAnimation]
						.method258(entity.anInt1527)) {
			int i = entity.anInt1548 - entity.anInt1547;
			int j = loopCycle - entity.anInt1547;
			int k = entity.anInt1543 * 128 + entity.boundDim * 64;
			int l = entity.anInt1545 * 128 + entity.boundDim * 64;
			int i1 = entity.anInt1544 * 128 + entity.boundDim * 64;
			int j1 = entity.anInt1546 * 128 + entity.boundDim * 64;
			entity.x = (k * (i - j) + i1 * j) / i;
			entity.y = (l * (i - j) + j1 * j) / i;
		}
		entity.anInt1503 = 0;
		if (entity.anInt1549 == 0)
			entity.turnDirection = 1024;
		if (entity.anInt1549 == 1)
			entity.turnDirection = 1536;
		if (entity.anInt1549 == 2)
			entity.turnDirection = 0;
		if (entity.anInt1549 == 3)
			entity.turnDirection = 512;
		entity.anInt1552 = entity.turnDirection;
	}

	private void getDegreesToTurn(Entity entity) {
		entity.movementAnimation = entity.standAnimIndex;
		if (entity.smallXYIndex == 0) {
			entity.anInt1503 = 0;
			return;
		}
		if (entity.emoteAnimation != -1 && entity.anInt1529 == 0) {
			Animation animation = Animation.animations[entity.emoteAnimation];
			if (entity.anInt1542 > 0 && animation.anInt363 == 0) {
				entity.anInt1503++;
				return;
			}
			if (entity.anInt1542 <= 0 && animation.anInt364 == 0) {
				entity.anInt1503++;
				return;
			}
		}
		int i = entity.x;
		int j = entity.y;
		int k = entity.pathX[entity.smallXYIndex - 1] * 128 + entity.boundDim
				* 64;
		int l = entity.pathY[entity.smallXYIndex - 1] * 128 + entity.boundDim
				* 64;
		if (k - i > 256 || k - i < -256 || l - j > 256 || l - j < -256) {
			entity.x = k;
			entity.y = l;
			return;
		}
		if (i < k) {
			if (j < l)
				entity.turnDirection = 1280;
			else if (j > l)
				entity.turnDirection = 1792;
			else
				entity.turnDirection = 1536;
		} else if (i > k) {
			if (j < l)
				entity.turnDirection = 768;
			else if (j > l)
				entity.turnDirection = 256;
			else
				entity.turnDirection = 512;
		} else if (j < l)
			entity.turnDirection = 1024;
		else
			entity.turnDirection = 0;
		int i1 = entity.turnDirection - entity.anInt1552 & 0x7ff;
		if (i1 > 1024)
			i1 -= 2048;
		int j1 = entity.turn180AnimIndex;
		if (i1 >= -256 && i1 <= 256)
			j1 = entity.walkAnimIndex;
		else if (i1 >= 256 && i1 < 768)
			j1 = entity.turn90CCWAnimIndex;
		else if (i1 >= -768 && i1 <= -256)
			j1 = entity.turn90CWAnimIndex;
		if (j1 == -1)
			j1 = entity.walkAnimIndex;
		entity.movementAnimation = j1;
		int k1 = 4;
		if (entity.anInt1552 != entity.turnDirection
				&& entity.interactingEntity == -1 && entity.degreesToTurn != 0)
			k1 = 2;
		if (entity.smallXYIndex > 2)
			k1 = 6;
		if (entity.smallXYIndex > 3)
			k1 = 8;
		if (entity.anInt1503 > 0 && entity.smallXYIndex > 1) {
			k1 = 8;
			entity.anInt1503--;
		}
		if (entity.pathRun[entity.smallXYIndex - 1])
			k1 <<= 1;
		if (k1 >= 8 && entity.movementAnimation == entity.walkAnimIndex
				&& entity.runAnimIndex != -1)
			entity.movementAnimation = entity.runAnimIndex;
		if (i < k) {
			entity.x += k1;
			if (entity.x > k)
				entity.x = k;
		} else if (i > k) {
			entity.x -= k1;
			if (entity.x < k)
				entity.x = k;
		}
		if (j < l) {
			entity.y += k1;
			if (entity.y > l)
				entity.y = l;
		} else if (j > l) {
			entity.y -= k1;
			if (entity.y < l)
				entity.y = l;
		}
		if (entity.x == k && entity.y == l) {
			entity.smallXYIndex--;
			if (entity.anInt1542 > 0)
				entity.anInt1542--;
		}
	}

	private void appendFocusDestination(Entity entity) {
		if (entity.degreesToTurn == 0)
			return;
		if (entity.interactingEntity != -1 && entity.interactingEntity < 32768) {
			Npc npc = npcs[entity.interactingEntity];
			if (npc != null) {
				int i1 = entity.x - npc.x;
				int k1 = entity.y - npc.y;
				if (i1 != 0 || k1 != 0)
					entity.turnDirection = (int) (Math.atan2(i1, k1) * 325.94900000000001D) & 0x7ff;
			}
		}
		if (entity.interactingEntity >= 32768) {
			int j = entity.interactingEntity - 32768;
			if (j == unknownInt10)
				j = internalLocalPlayerIndex;
			Player player = players[j];
			if (player != null) {
				int l1 = entity.x - player.x;
				int i2 = entity.y - player.y;
				if (l1 != 0 || i2 != 0)
					entity.turnDirection = (int) (Math.atan2(l1, i2) * 325.94900000000001D) & 0x7ff;
			}
		}
		if ((entity.anInt1538 != 0 || entity.anInt1539 != 0)
				&& (entity.smallXYIndex == 0 || entity.anInt1503 > 0)) {
			int k = entity.x - (entity.anInt1538 - baseX - baseX) * 64;
			int j1 = entity.y - (entity.anInt1539 - baseY - baseY) * 64;
			if (k != 0 || j1 != 0)
				entity.turnDirection = (int) (Math.atan2(k, j1) * 325.94900000000001D) & 0x7ff;
			entity.anInt1538 = 0;
			entity.anInt1539 = 0;
		}
		int l = entity.turnDirection - entity.anInt1552 & 0x7ff;
		if (l != 0) {
			if (l < entity.degreesToTurn || l > 2048 - entity.degreesToTurn)
				entity.anInt1552 = entity.turnDirection;
			else if (l > 1024)
				entity.anInt1552 -= entity.degreesToTurn;
			else
				entity.anInt1552 += entity.degreesToTurn;
			entity.anInt1552 &= 0x7ff;
			if (entity.movementAnimation == entity.standAnimIndex
					&& entity.anInt1552 != entity.turnDirection) {
				if (entity.standTurnAnimIndex != -1) {
					entity.movementAnimation = entity.standTurnAnimIndex;
					return;
				}
				entity.movementAnimation = entity.walkAnimIndex;
			}
		}
	}

	public void appendEmote(Entity entity) {
		entity.aBoolean1541 = false;
		if (entity.movementAnimation != -1) {
			if (entity.movementAnimation > Animation.animations.length)
				entity.movementAnimation = 0;
			Animation animation = Animation.animations[entity.movementAnimation];
			entity.anInt1519++;
			if (entity.displayedMovementFrames < animation.anInt352
					&& entity.anInt1519 > animation
							.method258(entity.displayedMovementFrames)) {
				entity.anInt1519 = 1;
				entity.displayedMovementFrames++;
				entity.nextIdleAnimationFrame++;
			}
			entity.nextIdleAnimationFrame = entity.displayedMovementFrames + 1;
			if (entity.nextIdleAnimationFrame >= animation.anInt352) {
				if (entity.nextIdleAnimationFrame >= animation.anInt352)
					entity.nextIdleAnimationFrame = 0;
			}
			if (entity.displayedMovementFrames >= animation.anInt352) {
				entity.anInt1519 = 1;
				entity.displayedMovementFrames = 0;
			}
		}
		if (entity.gfxId != -1 && loopCycle >= entity.anInt1523) {
			if (entity.anInt1521 < 0)
				entity.anInt1521 = 0;
			Animation animation_1 = SpotAnimation.cache[entity.gfxId].animationSequence;
			for (entity.anInt1522++; entity.anInt1521 < animation_1.anInt352
					&& entity.anInt1522 > animation_1
							.method258(entity.anInt1521); entity.anInt1521++)
				entity.anInt1522 -= animation_1.method258(entity.anInt1521);

			if (entity.anInt1521 >= animation_1.anInt352
					&& (entity.anInt1521 < 0 || entity.anInt1521 >= animation_1.anInt352))
				entity.gfxId = -1;
			entity.nextGraphicsAnimationFrame = entity.anInt1521 + 1;
			if (entity.nextGraphicsAnimationFrame >= animation_1.anInt352) {
				if (entity.nextGraphicsAnimationFrame < 0
						|| entity.nextGraphicsAnimationFrame >= animation_1.anInt352)
					entity.gfxId = -1;
			}
		}
		if (entity.emoteAnimation != -1 && entity.anInt1529 <= 1) {
			if (entity.emoteAnimation >= Animation.animations.length) {
				entity.emoteAnimation = -1;
			}
			Animation animation_2 = Animation.animations[entity.emoteAnimation];
			if (animation_2.anInt363 == 1 && entity.anInt1542 > 0
					&& entity.anInt1547 <= loopCycle
					&& entity.anInt1548 < loopCycle) {
				entity.anInt1529 = 1;
				return;
			}
		}
		if (entity.emoteAnimation != -1 && entity.anInt1529 == 0) {
			Animation animation_3 = Animation.animations[entity.emoteAnimation];
			for (entity.anInt1528++; entity.anInt1527 < animation_3.anInt352
					&& entity.anInt1528 > animation_3
							.method258(entity.anInt1527); entity.anInt1527++)
				entity.anInt1528 -= animation_3.method258(entity.anInt1527);

			if (entity.anInt1527 >= animation_3.anInt352) {
				entity.anInt1527 -= animation_3.anInt356;
				entity.anInt1530++;
				if (entity.anInt1530 >= animation_3.anInt362)
					entity.emoteAnimation = -1;
				if (entity.anInt1527 < 0
						|| entity.anInt1527 >= animation_3.anInt352)
					entity.emoteAnimation = -1;
			}
			entity.nextAnimationFrame = entity.anInt1527 + 1;
			if (entity.nextAnimationFrame >= animation_3.anInt352) {
				if (entity.anInt1530 >= animation_3.anInt362)
					entity.nextAnimationFrame = entity.anInt1527 + 1;
				if (entity.nextAnimationFrame < 0
						|| entity.nextAnimationFrame >= animation_3.anInt352)
					entity.nextAnimationFrame = entity.anInt1527;
			}
			entity.aBoolean1541 = animation_3.aBoolean358;
		}
		if (entity.anInt1529 > 0)
			entity.anInt1529--;
	}

	private void drawGameScreen() {
		if (fullscreenInterfaceID != -1
				&& (loadingStage == 2 || super.fullGameScreen != null)) {
			if (loadingStage == 2) {
				animateRSInterface(anInt945, fullscreenInterfaceID);
				if (openInterfaceId != -1) {
					animateRSInterface(anInt945, openInterfaceId);
				}
				anInt945 = 0;
				resetAllImageProducers();
				super.fullGameScreen.initDrawingArea();
				Rasterizer.anIntArray1472 = fullScreenTextureArray;
				Raster.clear();
				welcomeScreenRaised = true;
				if (openInterfaceId != -1) {
					Widget rsInterface_1 = Widget.interfaceCache[openInterfaceId];
					if (rsInterface_1.width == 512
							&& rsInterface_1.height == 334
							&& rsInterface_1.type == 0) {
						rsInterface_1.width = 765;
						rsInterface_1.height = 503;
					}
					drawInterface(0, 0, rsInterface_1, 8);
				}
				Widget rsInterface = Widget.interfaceCache[fullscreenInterfaceID];
				if (rsInterface.width == 512 && rsInterface.height == 334
						&& rsInterface.type == 0) {
					rsInterface.width = 765;
					rsInterface.height = 503;
				}
				drawInterface(0, 0, rsInterface, 8);
				if (!menuOpen) {
					processRightClick();
					drawTooltip();
				} else {
					drawMenu(frameMode == ScreenMode.FIXED ? 4 : 0,
							frameMode == ScreenMode.FIXED ? 4 : 0);
				}
			}
			drawCount++;
			super.fullGameScreen.drawGraphics(0, super.graphics, 0);
			return;
		} else {
			if (drawCount != 0) {
				setupGameplayScreen();
			}
		}
		if (welcomeScreenRaised) {
			welcomeScreenRaised = false;
			if (frameMode == ScreenMode.FIXED) {
				topFrame.drawGraphics(0, super.graphics, 0);
				leftFrame.drawGraphics(4, super.graphics, 0);
			}
			inputTaken = true;
			tabAreaAltered = true;
			if (loadingStage != 2) {
				if (frameMode == ScreenMode.FIXED) {
					gameScreenImageProducer.drawGraphics(
							frameMode == ScreenMode.FIXED ? 4 : 0,
							super.graphics, frameMode == ScreenMode.FIXED ? 4
									: 0);
					minimapImageProducer.drawGraphics(0, super.graphics, 516);
				}
			}
		}
		if (overlayInterfaceId != -1) {
			animateRSInterface(anInt945, overlayInterfaceId);
		}
		drawTabArea();
		if (backDialogueId == -1) {
			aClass9_1059.scrollPosition = anInt1211 - anInt1089 - 110;
			if (super.mouseX >= 496
					&& super.mouseX <= 511
					&& super.mouseY > (frameMode == ScreenMode.FIXED ? 345
							: frameHeight - 158))
				method65(494, 110, super.mouseX, super.mouseY
						- (frameMode == ScreenMode.FIXED ? 345
								: frameHeight - 158), aClass9_1059, 0, false,
						anInt1211);
			int i = anInt1211 - 110 - aClass9_1059.scrollPosition;
			if (i < 0) {
				i = 0;
			}
			if (i > anInt1211 - 110) {
				i = anInt1211 - 110;
			}
			if (anInt1089 != i) {
				anInt1089 = i;
				inputTaken = true;
			}
		}
		if (backDialogueId != -1) {
			boolean flag2 = animateRSInterface(anInt945, backDialogueId);
			if (flag2)
				inputTaken = true;
		}
		if (atInventoryInterfaceType == 3)
			inputTaken = true;
		if (activeInterfaceType == 3)
			inputTaken = true;
		if (clickToContinueString != null)
			inputTaken = true;
		if (menuOpen && menuScreenArea == 2)
			inputTaken = true;
		if (inputTaken) {
			drawChatArea();
			inputTaken = false;
		}
		if (loadingStage == 2)
			moveCameraWithPlayer();
		if (loadingStage == 2) {
			if (frameMode == ScreenMode.FIXED) {
				drawMinimap();
				minimapImageProducer.drawGraphics(0, super.graphics, 516);
			}
		}
		if (flashingSidebarId != -1)
			tabAreaAltered = true;
		if (tabAreaAltered) {
			if (flashingSidebarId != -1 && flashingSidebarId == tabID) {
				flashingSidebarId = -1;
				outgoing.createFrame(120);
				outgoing.writeByte(tabID);
			}
			tabAreaAltered = false;
			chatSettingImageProducer.initDrawingArea();
			gameScreenImageProducer.initDrawingArea();
		}
		anInt945 = 0;
	}

	private boolean buildFriendsListMenu(Widget class9) {
		int i = class9.contentType;
		if (i >= 1 && i <= 200 || i >= 701 && i <= 900) {
			if (i >= 801)
				i -= 701;
			else if (i >= 701)
				i -= 601;
			else if (i >= 101)
				i -= 101;
			else
				i--;
			menuActionName[menuActionRow] = "Remove @whi@" + friendsList[i];
			menuActionID[menuActionRow] = 792;
			menuActionRow++;
			menuActionName[menuActionRow] = "Message @whi@" + friendsList[i];
			menuActionID[menuActionRow] = 639;
			menuActionRow++;
			return true;
		}
		if (i >= 401 && i <= 500) {
			menuActionName[menuActionRow] = "Remove @whi@" + class9.defaultText;
			menuActionID[menuActionRow] = 322;
			menuActionRow++;
			return true;
		} else {
			return false;
		}
	}

	private void createStationaryGraphics() {
		SceneSpotAnim class30_sub2_sub4_sub3 = (SceneSpotAnim) incompleteAnimables
				.reverseGetFirst();
		for (; class30_sub2_sub4_sub3 != null; class30_sub2_sub4_sub3 = (SceneSpotAnim) incompleteAnimables
				.reverseGetNext())
			if (class30_sub2_sub4_sub3.anInt1560 != plane
					|| class30_sub2_sub4_sub3.aBoolean1567)
				class30_sub2_sub4_sub3.unlink();
			else if (loopCycle >= class30_sub2_sub4_sub3.anInt1564) {
				class30_sub2_sub4_sub3.method454(anInt945);
				if (class30_sub2_sub4_sub3.aBoolean1567)
					class30_sub2_sub4_sub3.unlink();
				else
					worldController.method285(class30_sub2_sub4_sub3.anInt1560,
							0, class30_sub2_sub4_sub3.anInt1563, -1,
							class30_sub2_sub4_sub3.anInt1562, 60,
							class30_sub2_sub4_sub3.anInt1561,
							class30_sub2_sub4_sub3, false);
			}

	}

	public void drawBlackBox(int xPos, int yPos) {
		Raster.drawPixels(71, yPos - 1, xPos - 2, 0x726451, 1);
		Raster.drawPixels(69, yPos, xPos + 174, 0x726451, 1);
		Raster.drawPixels(1, yPos - 2, xPos - 2, 0x726451, 178);
		Raster.drawPixels(1, yPos + 68, xPos, 0x726451, 174);
		Raster.drawPixels(71, yPos - 1, xPos - 1, 0x2E2B23, 1);
		Raster.drawPixels(71, yPos - 1, xPos + 175, 0x2E2B23, 1);
		Raster.drawPixels(1, yPos - 1, xPos, 0x2E2B23, 175);
		Raster.drawPixels(1, yPos + 69, xPos, 0x2E2B23, 175);
		Raster.method335(0, yPos, 174, 68, 220, xPos);
	}

	private void drawInterface(int scroll_y, int x, Widget rsInterface, int y) {
		if (rsInterface == null)
			rsInterface = Widget.interfaceCache[21356];
		if (rsInterface.type != 0 || rsInterface.children == null)
			return;
		if (rsInterface.hoverOnly && anInt1026 != rsInterface.id
				&& anInt1048 != rsInterface.id && anInt1039 != rsInterface.id)
			return;


		int clipLeft = Raster.topX;
		int clipTop = Raster.topY;
		int clipRight = Raster.bottomX;
		int clipBottom = Raster.bottomY;
		Raster.setDrawingArea(y + rsInterface.height, x, x + rsInterface.width,
				y);
		int childCount = rsInterface.children.length;
		int alpha = rsInterface.transparency;
		for (int childId = 0; childId < childCount; childId++) {
			int _x = rsInterface.childX[childId] + x;
			int _y = (rsInterface.childY[childId] + y) - scroll_y;
			Widget childInterface = Widget.interfaceCache[rsInterface.children[childId]];
			_x += childInterface.x;
			_y += childInterface.anInt265;
			if (childInterface.contentType > 0)
				drawFriendsListOrWelcomeScreen(childInterface);
			// here
			int[] IDs = { 1196, 1199, 1206, 1215, 1224, 1231, 1240, 1249, 1258,
					1267, 1274, 1283, 1573, 1290, 1299, 1308, 1315, 1324, 1333,
					1340, 1349, 1358, 1367, 1374, 1381, 1388, 1397, 1404, 1583,
					12038, 1414, 1421, 1430, 1437, 1446, 1453, 1460, 1469,
					15878, 1602, 1613, 1624, 7456, 1478, 1485, 1494, 1503,
					1512, 1521, 1530, 1544, 1553, 1563, 1593, 1635, 12426,
					12436, 12446, 12456, 6004, 18471,
					/* Ancients */
					12940, 12988, 13036, 12902, 12862, 13046, 12964, 13012,
					13054, 12920, 12882, 13062, 12952, 13000, 13070, 12912,
					12872, 13080, 12976, 13024, 13088, 12930, 12892, 13096 };
			for (int m5 = 0; m5 < IDs.length; m5++) {
				if (childInterface.id == IDs[m5] + 1) {
					if (m5 > 61)
						drawBlackBox(_x + 1, _y);
					else
						drawBlackBox(_x, _y + 1);
				}
			}
			int[] runeChildren = { 1202, 1203, 1209, 1210, 1211, 1218, 1219,
					1220, 1227, 1228, 1234, 1235, 1236, 1243, 1244, 1245, 1252,
					1253, 1254, 1261, 1262, 1263, 1270, 1271, 1277, 1278, 1279,
					1286, 1287, 1293, 1294, 1295, 1302, 1303, 1304, 1311, 1312,
					1318, 1319, 1320, 1327, 1328, 1329, 1336, 1337, 1343, 1344,
					1345, 1352, 1353, 1354, 1361, 1362, 1363, 1370, 1371, 1377,
					1378, 1384, 1385, 1391, 1392, 1393, 1400, 1401, 1407, 1408,
					1410, 1417, 1418, 1424, 1425, 1426, 1433, 1434, 1440, 1441,
					1442, 1449, 1450, 1456, 1457, 1463, 1464, 1465, 1472, 1473,
					1474, 1481, 1482, 1488, 1489, 1490, 1497, 1498, 1499, 1506,
					1507, 1508, 1515, 1516, 1517, 1524, 1525, 1526, 1533, 1534,
					1535, 1547, 1548, 1549, 1556, 1557, 1558, 1566, 1567, 1568,
					1576, 1577, 1578, 1586, 1587, 1588, 1596, 1597, 1598, 1605,
					1606, 1607, 1616, 1617, 1618, 1627, 1628, 1629, 1638, 1639,
					1640, 6007, 6008, 6011, 8673, 8674, 12041, 12042, 12429,
					12430, 12431, 12439, 12440, 12441, 12449, 12450, 12451,
					12459, 12460, 15881, 15882, 15885, 18474, 18475, 18478 };
			for (int r = 0; r < runeChildren.length; r++)
				if (childInterface.id == runeChildren[r])
					childInterface.modelZoom = 775;
			if (childInterface.type == Widget.TYPE_CONTAINER) {
				if (childInterface.scrollPosition > childInterface.scrollMax
						- childInterface.height)
					childInterface.scrollPosition = childInterface.scrollMax
							- childInterface.height;
				if (childInterface.scrollPosition < 0)
					childInterface.scrollPosition = 0;
				drawInterface(childInterface.scrollPosition, _x,
						childInterface, _y);
				if (childInterface.scrollMax > childInterface.height)
					drawScrollbar(childInterface.height,
							childInterface.scrollPosition, _y, _x
									+ childInterface.width,
							childInterface.scrollMax, false);
			} else if (childInterface.type != 1)
				if (childInterface.type == Widget.TYPE_INVENTORY) {
					int item = 0;
					for (int row = 0; row < childInterface.height; row++) {
						for (int column = 0; column < childInterface.width; column++) {
							int tileX = _x + column
									* (32 + childInterface.spritePaddingX);
							int tileY = _y + row
									* (32 + childInterface.spritePaddingY);
							if (item < 20) {
								tileX += childInterface.spritesX[item];
								tileY += childInterface.spritesY[item];
							}
							if (childInterface.inventoryItemId[item] > 0) {
								int differenceX = 0;
								int differenceY = 0;
								int itemId = childInterface.inventoryItemId[item] - 1;
								if (tileX > Raster.topX - 32
										&& tileX < Raster.bottomX
										&& tileY > Raster.topY - 32
										&& tileY < Raster.bottomY
										|| activeInterfaceType != 0
										&& anInt1085 == item) {
									int l9 = 0;
									if (itemSelected == 1 && anInt1283 == item
											&& anInt1284 == childInterface.id)
										l9 = 0xffffff;
									Sprite item_icon = ItemDefinition
											.getSprite(
													itemId,
													childInterface.invStackSizes[item],
													l9);
									if (item_icon != null) {
										if (activeInterfaceType != 0
												&& anInt1085 == item
												&& anInt1084 == childInterface.id) {
											differenceX = super.mouseX
													- anInt1087;
											differenceY = super.mouseY
													- anInt1088;
											if (differenceX < 5
													&& differenceX > -5)
												differenceX = 0;
											if (differenceY < 5
													&& differenceY > -5)
												differenceY = 0;
											if (anInt989 < 10) {
												differenceX = 0;
												differenceY = 0;
											}
											item_icon
													.drawSprite1(tileX
															+ differenceX,
															tileY + differenceY);
											if (tileY + differenceY < Raster.topY
													&& rsInterface.scrollPosition > 0) {
												int i10 = (anInt945 * (Raster.topY
														- tileY - differenceY)) / 3;
												if (i10 > anInt945 * 10)
													i10 = anInt945 * 10;
												if (i10 > rsInterface.scrollPosition)
													i10 = rsInterface.scrollPosition;
												rsInterface.scrollPosition -= i10;
												anInt1088 += i10;
											}
											if (tileY + differenceY + 32 > Raster.bottomY
													&& rsInterface.scrollPosition < rsInterface.scrollMax
															- rsInterface.height) {
												int j10 = (anInt945 * ((tileY
														+ differenceY + 32) - Raster.bottomY)) / 3;
												if (j10 > anInt945 * 10)
													j10 = anInt945 * 10;
												if (j10 > rsInterface.scrollMax
														- rsInterface.height
														- rsInterface.scrollPosition)
													j10 = rsInterface.scrollMax
															- rsInterface.height
															- rsInterface.scrollPosition;
												rsInterface.scrollPosition += j10;
												anInt1088 -= j10;
											}
										} else if (atInventoryInterfaceType != 0
												&& atInventoryIndex == item
												&& atInventoryInterface == childInterface.id)
											item_icon
													.drawSprite1(tileX, tileY);
										else
											item_icon
													.drawSprite(tileX, tileY);
										if (item_icon.maxWidth == 33
												|| childInterface.invStackSizes[item] != 1) {
											int k10 = childInterface.invStackSizes[item];

											smallText.method385(0,
													intToKOrMil(k10), tileY
															+ 10 + differenceY,
													tileX + 1 + differenceX);
											if (k10 >= 1)
												smallText.method385(0xFFFF00,
														intToKOrMil(k10), tileY
																+ 9
																+ differenceY,
														tileX + differenceX);
											if (k10 >= 100000)
												smallText.method385(0xFFFFFF,
														intToKOrMil(k10), tileY
																+ 9
																+ differenceY,
														tileX + differenceX);
											if (k10 >= 10000000)
												smallText.method385(0x00FF80,
														intToKOrMil(k10), tileY
																+ 9
																+ differenceY,
														tileX + differenceX);
										}
									}
								}
							} else if (childInterface.sprites != null
									&& item < 20) {
								Sprite image = childInterface.sprites[item];
								if (image != null)
									image.drawSprite(tileX,
											tileY);
							}
							item++;
						}
					}
				} else if (childInterface.type == Widget.TYPE_RECTANGLE) {
					boolean hover = false;
					if (anInt1039 == childInterface.id
							|| anInt1048 == childInterface.id
							|| anInt1026 == childInterface.id)
						hover = true;
					int colour;
					if (interfaceIsSelected(childInterface)) {
						colour = childInterface.secondaryColor;
						if (hover && childInterface.secondaryHoverColor != 0)
							colour = childInterface.secondaryHoverColor;
					} else {
						colour = childInterface.textColor;
						if (hover && childInterface.defaultHoverColor != 0)
							colour = childInterface.defaultHoverColor;
					}
					if (childInterface.opacity == 0) {
						if (childInterface.filled)
							Raster.drawPixels(childInterface.height, _y, _x,
									colour, childInterface.width);
						else
							Raster.fillPixels(_x, childInterface.width,
									childInterface.height, colour, _y);
					} else if (childInterface.filled)
						Raster.method335(colour, _y, childInterface.width,
								childInterface.height,
								256 - (childInterface.opacity & 0xff), _x);
					else
						Raster.method338(_y, childInterface.height,
								256 - (childInterface.opacity & 0xff), colour,
								childInterface.width, _x);
				} else if (childInterface.type == Widget.TYPE_TEXT) {
					GameFont textDrawingArea = childInterface.textDrawingAreas;
					String text = childInterface.defaultText;
					boolean flag1 = false;
					if (anInt1039 == childInterface.id
							|| anInt1048 == childInterface.id
							|| anInt1026 == childInterface.id)
						flag1 = true;
					int colour;
					if (interfaceIsSelected(childInterface)) {
						colour = childInterface.secondaryColor;
						if (flag1 && childInterface.secondaryHoverColor != 0)
							colour = childInterface.secondaryHoverColor;
						if (childInterface.secondaryText.length() > 0)
							text = childInterface.secondaryText;
					} else {
						colour = childInterface.textColor;
						if (flag1 && childInterface.defaultHoverColor != 0)
							colour = childInterface.defaultHoverColor;
					}
					if (childInterface.optionType == Widget.OPTION_CONTINUE && continuedDialogue) {
						text = "Please wait...";
						colour = childInterface.textColor;
					}
					if (Raster.width == 516) {
						if (colour == 0xffff00)
							colour = 255;
						if (colour == 49152)
							colour = 0xffffff;
					}
					if (frameMode != ScreenMode.FIXED) {
						if ((backDialogueId != -1 || dialogueId != -1 || childInterface.defaultText
								.contains("Click here to continue"))
								&& (rsInterface.id == backDialogueId || rsInterface.id == dialogueId)) {
							if (colour == 0xffff00) {
								colour = 255;
							}
							if (colour == 49152) {
								colour = 0xffffff;
							}
						}
					}
					if ((childInterface.parent == 1151)
							|| (childInterface.parent == 12855)) {
						switch (colour) {
						case 16773120:
							colour = 0xFE981F;
							break;
						case 7040819:
							colour = 0xAF6A1A;
							break;
						}
					}
					for (int l6 = _y + textDrawingArea.anInt1497; text.length() > 0; l6 += textDrawingArea.anInt1497) {
						if (text.indexOf("%") != -1) {
							do {
								int k7 = text.indexOf("%1");
								if (k7 == -1)
									break;
								if (childInterface.id < 4000
										|| childInterface.id > 5000
										&& childInterface.id != 13921
										&& childInterface.id != 13922
										&& childInterface.id != 12171
										&& childInterface.id != 12172)
									text = text.substring(0, k7)
											+ methodR(parseInterfaceOpcode(
													childInterface, 0))
											+ text.substring(k7 + 2);
								else
									text = text.substring(0, k7)
											+ interfaceIntToString(parseInterfaceOpcode(
													childInterface, 0))
											+ text.substring(k7 + 2);
							} while (true);
							do {
								int l7 = text.indexOf("%2");
								if (l7 == -1)
									break;
								text = text.substring(0, l7)
										+ interfaceIntToString(parseInterfaceOpcode(
												childInterface, 1))
										+ text.substring(l7 + 2);
							} while (true);
							do {
								int i8 = text.indexOf("%3");
								if (i8 == -1)
									break;
								text = text.substring(0, i8)
										+ interfaceIntToString(parseInterfaceOpcode(
												childInterface, 2))
										+ text.substring(i8 + 2);
							} while (true);
							do {
								int j8 = text.indexOf("%4");
								if (j8 == -1)
									break;
								text = text.substring(0, j8)
										+ interfaceIntToString(parseInterfaceOpcode(
												childInterface, 3))
										+ text.substring(j8 + 2);
							} while (true);
							do {
								int k8 = text.indexOf("%5");
								if (k8 == -1)
									break;
								text = text.substring(0, k8)
										+ interfaceIntToString(parseInterfaceOpcode(
												childInterface, 4))
										+ text.substring(k8 + 2);
							} while (true);
						}
						int l8 = text.indexOf("\\n");
						String s1;
						if (l8 != -1) {
							s1 = text.substring(0, l8);
							text = text.substring(l8 + 2);
						} else {
							s1 = text;
							text = "";
						}
						if (childInterface.centerText)
							textDrawingArea.method382(colour, _x
									+ childInterface.width / 2, s1, l6,
									childInterface.textShadow);
						else
							textDrawingArea.drawTextWithPotentialShadow(
									childInterface.textShadow, _x, colour, s1,
									l6);
					}
				} else if (childInterface.type == Widget.TYPE_SPRITE) {
					Sprite sprite;
					if (interfaceIsSelected(childInterface))
						sprite = childInterface.enabledSprite;
					else
						sprite = childInterface.disabledSprite;
					if (spellSelected == 1 && childInterface.id == spellID
							&& spellID != 0 && sprite != null) {
						sprite.drawSprite(_x, _y, 0xffffff);
					} else {
						if (sprite != null)
							if (childInterface.drawsTransparent) {
								sprite.drawTransparentSprite(_x, _y, alpha);
							} else {
								sprite.drawSprite(_x, _y);
							}
					}
					if (autocast && childInterface.id == autoCastId)
						cacheSprite[43].drawSprite(_x - 3, _y - 3);
					if (sprite != null)
						if (childInterface.drawsTransparent) {
							sprite.drawSprite1(_x, _y);
						} else {
							sprite.drawSprite(_x, _y);
						}
				} else if (childInterface.type == Widget.TYPE_MODEL) {
					int centreX = Rasterizer.textureInt1;
					int centreY = Rasterizer.textureInt2;
					Rasterizer.textureInt1 = _x + childInterface.width / 2;
					Rasterizer.textureInt2 = _y + childInterface.height / 2;
					int sine = Rasterizer.anIntArray1470[childInterface.modelRotation1]
							* childInterface.modelZoom >> 16;
					int cosine = Rasterizer.anIntArray1471[childInterface.modelRotation1]
							* childInterface.modelZoom >> 16;
					boolean selected = interfaceIsSelected(childInterface);
					int emoteAnimation;
					if (selected)
						emoteAnimation = childInterface.anInt258;
					else
						emoteAnimation = childInterface.anInt257;
					Model model;
					if (emoteAnimation == -1) {
						model = childInterface.method209(-1, -1, selected);
					} else {
						Animation animation = Animation.animations[emoteAnimation];
						model = childInterface
								.method209(
										animation.anIntArray354[childInterface.anInt246],
										animation.anIntArray353[childInterface.anInt246],
										selected);
					}
					if (model != null)
						model.method482(childInterface.modelRotation2, 0,
								childInterface.modelRotation1, 0, sine, cosine);
					Rasterizer.textureInt1 = centreX;
					Rasterizer.textureInt2 = centreY;
				} else if (childInterface.type == Widget.TYPE_ITEM_LIST) {
					GameFont font = childInterface.textDrawingAreas;
					int slot = 0;
					for (int row = 0; row < childInterface.height; row++) {
						for (int column = 0; column < childInterface.width; column++) {
							if (childInterface.inventoryItemId[slot] > 0) {
								ItemDefinition item = ItemDefinition
										.lookup(childInterface.inventoryItemId[slot] - 1);
								String name = item.name;
								if (item.stackable
										|| childInterface.invStackSizes[slot] != 1)
									name = name
											+ " x"
											+ intToKOrMilLongName(childInterface.invStackSizes[slot]);
								int __x = _x + column
										* (115 + childInterface.spritePaddingX);
								int __y = _y + row
										* (12 + childInterface.spritePaddingY);
								if (childInterface.centerText)
									font.method382(childInterface.textColor,
											__x + childInterface.width / 2,
											name, __y,
											childInterface.textShadow);
								else
									font.drawTextWithPotentialShadow(
											childInterface.textShadow, __x,
											childInterface.textColor, name, __y);
							}
							slot++;
						}
					}
				} else if (childInterface.type == 9) {
					drawHoverBox(_x, _y, childInterface.popupString);
				} else if (childInterface.type == 8
						&& (anInt1500 == childInterface.id
								|| anInt1044 == childInterface.id || anInt1129 == childInterface.id)
						&& anInt1501 == 0 && !menuOpen) {
					int boxWidth = 0;
					int boxHeight = 0;
					GameFont font = regularText;
					for (String s1 = childInterface.defaultText; s1.length() > 0;) {
						if (s1.indexOf("%") != -1) {
							do {
								int k7 = s1.indexOf("%1");
								if (k7 == -1)
									break;
								s1 = s1.substring(0, k7)
										+ interfaceIntToString(parseInterfaceOpcode(
												childInterface, 0))
										+ s1.substring(k7 + 2);
							} while (true);
							do {
								int l7 = s1.indexOf("%2");
								if (l7 == -1)
									break;
								s1 = s1.substring(0, l7)
										+ interfaceIntToString(parseInterfaceOpcode(
												childInterface, 1))
										+ s1.substring(l7 + 2);
							} while (true);
							do {
								int i8 = s1.indexOf("%3");
								if (i8 == -1)
									break;
								s1 = s1.substring(0, i8)
										+ interfaceIntToString(parseInterfaceOpcode(
												childInterface, 2))
										+ s1.substring(i8 + 2);
							} while (true);
							do {
								int j8 = s1.indexOf("%4");
								if (j8 == -1)
									break;
								s1 = s1.substring(0, j8)
										+ interfaceIntToString(parseInterfaceOpcode(
												childInterface, 3))
										+ s1.substring(j8 + 2);
							} while (true);
							do {
								int k8 = s1.indexOf("%5");
								if (k8 == -1)
									break;
								s1 = s1.substring(0, k8)
										+ interfaceIntToString(parseInterfaceOpcode(
												childInterface, 4))
										+ s1.substring(k8 + 2);
							} while (true);
						}
						int l7 = s1.indexOf("\\n");
						String s4;
						if (l7 != -1) {
							s4 = s1.substring(0, l7);
							s1 = s1.substring(l7 + 2);
						} else {
							s4 = s1;
							s1 = "";
						}
						int j10 = font.getTextWidth(s4);
						if (j10 > boxWidth) {
							boxWidth = j10;
						}
						boxHeight += font.anInt1497 + 1;
					}
					boxWidth += 6;
					boxHeight += 7;
					int xPos = (_x + childInterface.width) - 5 - boxWidth;
					int yPos = _y + childInterface.height + 5;
					if (xPos < _x + 5) {
						xPos = _x + 5;
					}
					if (xPos + boxWidth > x + rsInterface.width) {
						xPos = (x + rsInterface.width) - boxWidth;
					}
					if (yPos + boxHeight > y + rsInterface.height) {
						yPos = (_y - boxHeight);
					}
					Raster.drawPixels(boxHeight, yPos, xPos, 0xFFFFA0, boxWidth);
					Raster.fillPixels(xPos, boxWidth, boxHeight, 0, yPos);
					String s2 = childInterface.defaultText;
					for (int j11 = yPos + font.anInt1497 + 2; s2.length() > 0; j11 += font.anInt1497 + 1) {// anInt1497
						if (s2.indexOf("%") != -1) {
							do {
								int k7 = s2.indexOf("%1");
								if (k7 == -1)
									break;
								s2 = s2.substring(0, k7)
										+ interfaceIntToString(parseInterfaceOpcode(
												childInterface, 0))
										+ s2.substring(k7 + 2);
							} while (true);
							do {
								int l7 = s2.indexOf("%2");
								if (l7 == -1)
									break;
								s2 = s2.substring(0, l7)
										+ interfaceIntToString(parseInterfaceOpcode(
												childInterface, 1))
										+ s2.substring(l7 + 2);
							} while (true);
							do {
								int i8 = s2.indexOf("%3");
								if (i8 == -1)
									break;
								s2 = s2.substring(0, i8)
										+ interfaceIntToString(parseInterfaceOpcode(
												childInterface, 2))
										+ s2.substring(i8 + 2);
							} while (true);
							do {
								int j8 = s2.indexOf("%4");
								if (j8 == -1)
									break;
								s2 = s2.substring(0, j8)
										+ interfaceIntToString(parseInterfaceOpcode(
												childInterface, 3))
										+ s2.substring(j8 + 2);
							} while (true);
							do {
								int k8 = s2.indexOf("%5");
								if (k8 == -1)
									break;
								s2 = s2.substring(0, k8)
										+ interfaceIntToString(parseInterfaceOpcode(
												childInterface, 4))
										+ s2.substring(k8 + 2);
							} while (true);
						}
						int l11 = s2.indexOf("\\n");
						String s5;
						if (l11 != -1) {
							s5 = s2.substring(0, l11);
							s2 = s2.substring(l11 + 2);
						} else {
							s5 = s2;
							s2 = "";
						}
						if (childInterface.centerText) {
							font.method382(yPos, xPos + childInterface.width
									/ 2, s5, j11, false);
						} else {
							if (s5.contains("\\r")) {
								String text = s5
										.substring(0, s5.indexOf("\\r"));
								String text2 = s5
										.substring(s5.indexOf("\\r") + 2);
								font.drawTextWithPotentialShadow(false,
										xPos + 3, 0, text, j11);
								int rightX = boxWidth + xPos
										- font.getTextWidth(text2) - 2;
								font.drawTextWithPotentialShadow(false, rightX,
										0, text2, j11);
								System.out.println("Box: " + boxWidth + "");
							} else
								font.drawTextWithPotentialShadow(false,
										xPos + 3, 0, s5, j11);
						}
					}
				}
		}
		Raster.setDrawingArea(clipBottom, clipLeft, clipRight, clipTop);
	}

	private void randomizeBackground(Background background) {
		int j = 256;
		for (int k = 0; k < anIntArray1190.length; k++)
			anIntArray1190[k] = 0;

		for (int l = 0; l < 5000; l++) {
			int i1 = (int) (Math.random() * 128D * (double) j);
			anIntArray1190[i1] = (int) (Math.random() * 256D);
		}
		for (int j1 = 0; j1 < 20; j1++) {
			for (int k1 = 1; k1 < j - 1; k1++) {
				for (int i2 = 1; i2 < 127; i2++) {
					int k2 = i2 + (k1 << 7);
					anIntArray1191[k2] = (anIntArray1190[k2 - 1]
							+ anIntArray1190[k2 + 1] + anIntArray1190[k2 - 128] + anIntArray1190[k2 + 128]) / 4;
				}

			}
			int ai[] = anIntArray1190;
			anIntArray1190 = anIntArray1191;
			anIntArray1191 = ai;
		}
		if (background != null) {
			int l1 = 0;
			for (int j2 = 0; j2 < background.anInt1453; j2++) {
				for (int l2 = 0; l2 < background.anInt1452; l2++)
					if (background.aByteArray1450[l1++] != 0) {
						int i3 = l2 + 16 + background.anInt1454;
						int j3 = j2 + 16 + background.anInt1455;
						int k3 = i3 + (j3 << 7);
						anIntArray1190[k3] = 0;
					}
			}
		}
	}

	private void appendPlayerUpdateMask(int i, int j, Buffer stream,
			Player player) {
		if ((i & 0x400) != 0) {
			player.anInt1543 = stream.readUByteS();
			player.anInt1545 = stream.readUByteS();
			player.anInt1544 = stream.readUByteS();
			player.anInt1546 = stream.readUByteS();
			player.anInt1547 = stream.readLEUShortA() + loopCycle;
			player.anInt1548 = stream.readUShortA() + loopCycle;
			player.anInt1549 = stream.readUByteS();
			player.resetPath();
		}
		if ((i & 0x100) != 0) {
			player.gfxId = stream.readLEUShort();
			int k = stream.readInt();
			player.anInt1524 = k >> 16;
			player.anInt1523 = loopCycle + (k & 0xffff);
			player.anInt1521 = 0;
			player.anInt1522 = 0;
			if (player.anInt1523 > loopCycle)
				player.anInt1521 = -1;
			if (player.gfxId == 65535)
				player.gfxId = -1;
		}
		if ((i & 8) != 0) {
			int l = stream.readLEUShort();
			if (l == 65535)
				l = -1;
			int i2 = stream.readNegUByte();
			if (l == player.emoteAnimation && l != -1) {
				int i3 = Animation.animations[l].anInt365;
				if (i3 == 1) {
					player.anInt1527 = 0;
					player.anInt1528 = 0;
					player.anInt1529 = i2;
					player.anInt1530 = 0;
				}
				if (i3 == 2)
					player.anInt1530 = 0;
			} else if (l == -1
					|| player.emoteAnimation == -1
					|| Animation.animations[l].anInt359 >= Animation.animations[player.emoteAnimation].anInt359) {
				player.emoteAnimation = l;
				player.anInt1527 = 0;
				player.anInt1528 = 0;
				player.anInt1529 = i2;
				player.anInt1530 = 0;
				player.anInt1542 = player.smallXYIndex;
			}
		}
		if ((i & 4) != 0) {
			player.spokenText = stream.readString();
			if (player.spokenText.charAt(0) == '~') {
				player.spokenText = player.spokenText.substring(1);
				pushMessage(player.spokenText, 2, player.name);
			} else if (player == localPlayer)
				pushMessage(player.spokenText, 2, player.name);
			player.textColour = 0;
			player.textEffect = 0;
			player.textCycle = 150;
		}
		if ((i & 0x80) != 0) {
			int i1 = stream.readLEUShort();
			int j2 = stream.readUnsignedByte();
			int j3 = stream.readNegUByte();
			int k3 = stream.currentPosition;
			if (player.name != null && player.visible) {
				long l3 = TextClass.longForName(player.name);
				boolean flag = false;
				if (j2 <= 1) {
					for (int i4 = 0; i4 < ignoreCount; i4++) {
						if (ignoreListAsLongs[i4] != l3)
							continue;
						flag = true;
						break;
					}

				}
				if (!flag && anInt1251 == 0)
					try {
						aStream_834.currentPosition = 0;
						stream.readReverseData(aStream_834.payload, j3, 0);
						aStream_834.currentPosition = 0;
						String s = TextInput.method525(j3, aStream_834);
						// s = Censor.doCensor(s);
						player.spokenText = s;
						player.textColour = i1 >> 8;
						player.privelage = j2;
						player.textEffect = i1 & 0xff;
						player.textCycle = 150;
						if (j2 == 2 || j2 == 3)
							pushMessage(s, 1, "@cr2@" + player.name);
						else if (j2 == 1)
							pushMessage(s, 1, "@cr1@" + player.name);
						else
							pushMessage(s, 2, player.name);
					} catch (Exception exception) {
						Signlink.reporterror("cde2");
					}
			}
			stream.currentPosition = k3 + j3;
		}
		if ((i & 1) != 0) {
			player.interactingEntity = stream.readLEUShort();
			if (player.interactingEntity == 65535)
				player.interactingEntity = -1;
		}
		if ((i & 0x10) != 0) {
			int j1 = stream.readNegUByte();
			byte abyte0[] = new byte[j1];
			Buffer stream_1 = new Buffer(abyte0);
			stream.readBytes(j1, 0, abyte0);
			playerSynchronizationBuffers[j] = stream_1;
			player.updatePlayer(stream_1);
		}
		if ((i & 2) != 0) {
			player.anInt1538 = stream.readLEUShortA();
			player.anInt1539 = stream.readLEUShort();
		}
		if ((i & 0x20) != 0) {
			int k1 = stream.readUnsignedByte();
			int k2 = stream.readUByteA();
			player.updateHitData(k2, k1, loopCycle);
			player.loopCycleStatus = loopCycle + 300;
			player.currentHealth = stream.readNegUByte();
			player.maxHealth = stream.readUnsignedByte();
		}
		if ((i & 0x200) != 0) {
			int l1 = stream.readUnsignedByte();
			int l2 = stream.readUByteS();
			player.updateHitData(l2, l1, loopCycle);
			player.loopCycleStatus = loopCycle + 300;
			player.currentHealth = stream.readUnsignedByte();
			player.maxHealth = stream.readNegUByte();
		}
	}

	private void checkForGameUsages() {
		try {
			int j = localPlayer.x + anInt1278;
			int k = localPlayer.y + anInt1131;
			if (anInt1014 - j < -500 || anInt1014 - j > 500
					|| anInt1015 - k < -500 || anInt1015 - k > 500) {
				anInt1014 = j;
				anInt1015 = k;
			}
			if (anInt1014 != j)
				anInt1014 += (j - anInt1014) / 16;
			if (anInt1015 != k)
				anInt1015 += (k - anInt1015) / 16;
			if (super.keyArray[1] == 1)
				anInt1186 += (-24 - anInt1186) / 2;
			else if (super.keyArray[2] == 1)
				anInt1186 += (24 - anInt1186) / 2;
			else
				anInt1186 /= 2;
			if (super.keyArray[3] == 1)
				anInt1187 += (12 - anInt1187) / 2;
			else if (super.keyArray[4] == 1)
				anInt1187 += (-12 - anInt1187) / 2;
			else
				anInt1187 /= 2;
			cameraHorizontal = cameraHorizontal + anInt1186 / 2 & 0x7ff;
			anInt1184 += anInt1187 / 2;
			if (anInt1184 < 128)
				anInt1184 = 128;
			if (anInt1184 > 383)
				anInt1184 = 383;
			int l = anInt1014 >> 7;
			int i1 = anInt1015 >> 7;
			int j1 = method42(plane, anInt1015, anInt1014);
			int k1 = 0;
			if (l > 3 && i1 > 3 && l < 100 && i1 < 100) {
				for (int l1 = l - 4; l1 <= l + 4; l1++) {
					for (int k2 = i1 - 4; k2 <= i1 + 4; k2++) {
						int l2 = plane;
						if (l2 < 3 && (byteGroundArray[1][l1][k2] & 2) == 2)
							l2++;
						int i3 = j1 - intGroundArray[l2][l1][k2];
						if (i3 > k1)
							k1 = i3;
					}

				}

			}
			anInt1005++;
			if (anInt1005 > 1512) {
				anInt1005 = 0;
				outgoing.createFrame(77);
				outgoing.writeByte(0);
				int i2 = outgoing.currentPosition;
				outgoing.writeByte((int) (Math.random() * 256D));
				outgoing.writeByte(101);
				outgoing.writeByte(233);
				outgoing.writeShort(45092);
				if ((int) (Math.random() * 2D) == 0)
					outgoing.writeShort(35784);
				outgoing.writeByte((int) (Math.random() * 256D));
				outgoing.writeByte(64);
				outgoing.writeByte(38);
				outgoing.writeShort((int) (Math.random() * 65536D));
				outgoing.writeShort((int) (Math.random() * 65536D));
				outgoing.writeBytes(outgoing.currentPosition - i2);
			}
			int j2 = k1 * 192;
			if (j2 > 0x17f00)
				j2 = 0x17f00;
			if (j2 < 32768)
				j2 = 32768;
			if (j2 > anInt984) {
				anInt984 += (j2 - anInt984) / 24;
				return;
			}
			if (j2 < anInt984) {
				anInt984 += (j2 - anInt984) / 80;
			}
		} catch (Exception _ex) {
			Signlink.reporterror("glfc_ex " + localPlayer.x + ","
					+ localPlayer.y + "," + anInt1014 + "," + anInt1015 + ","
					+ anInt1069 + "," + anInt1070 + "," + baseX + "," + baseY);
			throw new RuntimeException("eek");
		}
	}

	public void processDrawing() {
		if (rsAlreadyLoaded || loadingError || genericLoadingError) {
			showErrorScreen();
			return;
		}
		if (!loggedIn)
			drawLoginScreen(false);
		else
			drawGameScreen();
		anInt1213 = 0;
	}

	private boolean isFriendOrSelf(String s) {
		if (s == null)
			return false;
		for (int i = 0; i < friendsCount; i++)
			if (s.equalsIgnoreCase(friendsList[i]))
				return true;
		return s.equalsIgnoreCase(localPlayer.name);
	}

	private static String combatDiffColor(int i, int j) {
		int k = i - j;
		if (k < -9)
			return "@red@";
		if (k < -6)
			return "@or3@";
		if (k < -3)
			return "@or2@";
		if (k < 0)
			return "@or1@";
		if (k > 9)
			return "@gre@";
		if (k > 6)
			return "@gr3@";
		if (k > 3)
			return "@gr2@";
		if (k > 0)
			return "@gr1@";
		else
			return "@yel@";
	}

	private void setWaveVolume(int i) {
		Signlink.wavevol = i;
	}

	private void draw3dScreen() {
		if (counterOn) {
			drawCounterOnScreen();
		}
		if (showChatComponents) {
			drawSplitPrivateChat();
		}
		if (crossType == 1) {
			int offSet = frameMode == ScreenMode.FIXED ? 4 : 0;
			crosses[crossIndex / 100].drawSprite(crossX - 8 - offSet, crossY
					- 8 - offSet);
			anInt1142++;
			if (anInt1142 > 67) {
				anInt1142 = 0;
				outgoing.createFrame(78);
			}
		}
		if (crossType == 2) {
			int offSet = frameMode == ScreenMode.FIXED ? 4 : 0;
			crosses[4 + crossIndex / 100].drawSprite(crossX - 8 - offSet,
					crossY - 8 - offSet);
		}
		if (openWalkableInterface != -1) {
			animateRSInterface(anInt945, openWalkableInterface);
			if (openWalkableInterface == 197 && frameMode != ScreenMode.FIXED) {
				skullIcons[0].drawSprite(frameWidth - 157, 168);
				String text = Widget.interfaceCache[199].defaultText.replace(
						"@yel@", "");
				regularText.drawChatInput(0xE1981D, frameWidth - 165, text,
						207, true);
			} else if (openWalkableInterface == 201
					&& frameMode != ScreenMode.FIXED) {
				drawInterface(0, frameWidth - 560,
						Widget.interfaceCache[openWalkableInterface], -109);
			} else {
				drawInterface(0, frameMode == ScreenMode.FIXED ? 0
						: (frameWidth / 2) - 356,
						Widget.interfaceCache[openWalkableInterface],
						frameMode == ScreenMode.FIXED ? 0
								: (frameHeight / 2) - 230);
			}
		}
		if (openInterfaceId != -1) {
			animateRSInterface(anInt945, openInterfaceId);
			drawInterface(0, frameMode == ScreenMode.FIXED ? 0
					: (frameWidth / 2) - 356,
					Widget.interfaceCache[openInterfaceId],
					frameMode == ScreenMode.FIXED ? 0 : (frameHeight / 2) - 230);
		}
		if (!menuOpen) {
			processRightClick();
			drawTooltip();
		} else if (menuScreenArea == 0) {
			drawMenu(frameMode == ScreenMode.FIXED ? 4 : 0,
					frameMode == ScreenMode.FIXED ? 4 : 0);
		}
		if (multicombat == 1) {
			multiOverlay.drawSprite(frameMode == ScreenMode.FIXED ? 472
					: frameWidth - 85, frameMode == ScreenMode.FIXED ? 296
					: 186);
		}
		int x = baseX + (localPlayer.x - 6 >> 7);
		int y = baseY + (localPlayer.y - 6 >> 7);
		final String screenMode = frameMode == ScreenMode.FIXED ? "Fixed"
				: "Resizable";
		if (Configuration.clientData) {
			int textColour = 0xffff00;
			int fpsColour = 0xffff00;
			if (super.fps < 15) {
				fpsColour = 0xff0000;
			}
			// regularText.method385(textColour,
			// "frameWidth: " + (mouseX - frameWidth) + ", frameHeight: " +
			// (mouseY - frameHeight),
			// frameHeight - 271, 5);
			regularText.method385(textColour, "Client Zoom: " + cameraZoom, 90,
					frameMode == ScreenMode.FIXED ? 5 : frameWidth - 5);
			regularText.method385(fpsColour, "Fps: " + super.fps, 12,
					frameMode == ScreenMode.FIXED ? 470 : frameWidth - 265);
			Runtime runtime = Runtime.getRuntime();
			int clientMemory = (int) ((runtime.totalMemory() - runtime
					.freeMemory()) / 1024L);
			regularText.method385(textColour, "Mem: " + clientMemory + "k", 27,
					frameMode == ScreenMode.FIXED ? 428 : frameWidth - 265);
			regularText.method385(textColour, "Mouse X: " + super.mouseX
					+ " , Mouse Y: " + super.mouseY, 30,
					frameMode == ScreenMode.FIXED ? 5 : frameWidth - 5);
			regularText.method385(textColour, "Coords: " + x + ", " + y, 45,
					frameMode == ScreenMode.FIXED ? 5 : frameWidth - 5);
			regularText.method385(textColour,
					"Client Mode: " + screenMode + "", 60,
					frameMode == ScreenMode.FIXED ? 5 : frameWidth - 5);
			regularText.method385(textColour, "Client Resolution: "
					+ frameWidth + "x" + frameHeight, 75,
					frameMode == ScreenMode.FIXED ? 5 : frameWidth - 5);
		}
		if (systemUpdateTime != 0) {
			int j = systemUpdateTime / 50;
			int l = j / 60;
			int yOffset = frameMode == ScreenMode.FIXED ? 0 : frameHeight - 498;
			j %= 60;
			if (j < 10)
				regularText.method385(0xffff00, "System update in: " + l + ":0"
						+ j, 329 + yOffset, 4);
			else
				regularText.method385(0xffff00, "System update in: " + l + ":"
						+ j, 329 + yOffset, 4);
			anInt849++;
			if (anInt849 > 75) {
				anInt849 = 0;
				outgoing.createFrame(148);
			}
		}
	}

	private void addIgnore(long l) {
		try {
			if (l == 0L)
				return;
			if (ignoreCount >= 100) {
				pushMessage("Your ignore list is full. Max of 100 hit", 0, "");
				return;
			}
			String s = TextClass.fixName(TextClass.nameForLong(l));
			for (int j = 0; j < ignoreCount; j++)
				if (ignoreListAsLongs[j] == l) {
					pushMessage(s + " is already on your ignore list", 0, "");
					return;
				}
			for (int k = 0; k < friendsCount; k++)
				if (friendsListAsLongs[k] == l) {
					pushMessage("Please remove " + s
							+ " from your friend list first", 0, "");
					return;
				}

			ignoreListAsLongs[ignoreCount++] = l;
			outgoing.createFrame(133);
			outgoing.writeLong(l);
			return;
		} catch (RuntimeException runtimeexception) {
			Signlink.reporterror("45688, " + l + ", " + 4 + ", "
					+ runtimeexception.toString());
		}
		throw new RuntimeException();
	}

	private void updatePlayerInstances() {
		for (int i = -1; i < playerCount; i++) {
			int j;
			if (i == -1)
				j = internalLocalPlayerIndex;
			else
				j = playerIndices[i];
			Player player = players[j];
			if (player != null)
				entityUpdateBlock(player);
		}

	}

	private void method115() {
		if (loadingStage == 2) {
			for (TemporaryObject class30_sub1 = (TemporaryObject) spawns
					.reverseGetFirst(); class30_sub1 != null; class30_sub1 = (TemporaryObject) spawns
					.reverseGetNext()) {
				if (class30_sub1.anInt1294 > 0)
					class30_sub1.anInt1294--;
				if (class30_sub1.anInt1294 == 0) {
					if (class30_sub1.anInt1299 < 0
							|| ObjectManager.method178(class30_sub1.anInt1299,
									class30_sub1.anInt1301)) {
						method142(class30_sub1.anInt1298,
								class30_sub1.anInt1295, class30_sub1.anInt1300,
								class30_sub1.anInt1301, class30_sub1.anInt1297,
								class30_sub1.anInt1296, class30_sub1.anInt1299);
						class30_sub1.unlink();
					}
				} else {
					if (class30_sub1.anInt1302 > 0)
						class30_sub1.anInt1302--;
					if (class30_sub1.anInt1302 == 0
							&& class30_sub1.anInt1297 >= 1
							&& class30_sub1.anInt1298 >= 1
							&& class30_sub1.anInt1297 <= 102
							&& class30_sub1.anInt1298 <= 102
							&& (class30_sub1.anInt1291 < 0 || ObjectManager
									.method178(class30_sub1.anInt1291,
											class30_sub1.anInt1293))) {
						method142(class30_sub1.anInt1298,
								class30_sub1.anInt1295, class30_sub1.anInt1292,
								class30_sub1.anInt1293, class30_sub1.anInt1297,
								class30_sub1.anInt1296, class30_sub1.anInt1291);
						class30_sub1.anInt1302 = -1;
						if (class30_sub1.anInt1291 == class30_sub1.anInt1299
								&& class30_sub1.anInt1299 == -1)
							class30_sub1.unlink();
						else if (class30_sub1.anInt1291 == class30_sub1.anInt1299
								&& class30_sub1.anInt1292 == class30_sub1.anInt1300
								&& class30_sub1.anInt1293 == class30_sub1.anInt1301)
							class30_sub1.unlink();
					}
				}
			}

		}
	}

	private void determineMenuSize() {
		int boxLength = boldText.getTextWidth("Choose option");
		for (int row = 0; row < menuActionRow; row++) {
			int actionLength = boldText.getTextWidth(menuActionName[row]);
			if (actionLength > boxLength)
				boxLength = actionLength;
		}
		boxLength += 8;
		int offset = 15 * menuActionRow + 21;
		if (super.saveClickX > 0 && super.saveClickY > 0
				&& super.saveClickX < frameWidth
				&& super.saveClickY < frameHeight) {
			int xClick = super.saveClickX - boxLength / 2;
			if (xClick + boxLength > frameWidth - 4) {
				xClick = frameWidth - 4 - boxLength;
			}
			if (xClick < 0) {
				xClick = 0;
			}
			int yClick = super.saveClickY - 0;
			if (yClick + offset > frameHeight - 6) {
				yClick = frameHeight - 6 - offset;
			}
			if (yClick < 0) {
				yClick = 0;
			}
			menuOpen = true;
			menuOffsetX = xClick;
			menuOffsetY = yClick;
			menuWidth = boxLength;
			menuHeight = 15 * menuActionRow + 22;
		}
	}

	private void updatePlayerMovement(Buffer stream) {
		stream.initBitAccess();
		int j = stream.readBits(1);
		if (j == 0)
			return;
		int k = stream.readBits(2);
		if (k == 0) {
			anIntArray894[anInt893++] = internalLocalPlayerIndex;
			return;
		}
		if (k == 1) {
			int l = stream.readBits(3);
			localPlayer.moveInDir(false, l);
			int k1 = stream.readBits(1);
			if (k1 == 1)
				anIntArray894[anInt893++] = internalLocalPlayerIndex;
			return;
		}
		if (k == 2) {
			int i1 = stream.readBits(3);
			localPlayer.moveInDir(true, i1);
			int l1 = stream.readBits(3);
			localPlayer.moveInDir(true, l1);
			int j2 = stream.readBits(1);
			if (j2 == 1)
				anIntArray894[anInt893++] = internalLocalPlayerIndex;
			return;
		}
		if (k == 3) {
			plane = stream.readBits(2);
			int j1 = stream.readBits(1);
			int i2 = stream.readBits(1);
			if (i2 == 1)
				anIntArray894[anInt893++] = internalLocalPlayerIndex;
			int k2 = stream.readBits(7);
			int l2 = stream.readBits(7);
			localPlayer.setPos(l2, k2, j1 == 1);
		}
	}

	private void nullLoader() {
		aBoolean831 = false;
		while (drawingFlames) {
			aBoolean831 = false;
			try {
				Thread.sleep(50L);
			} catch (Exception _ex) {
			}
		}
		aBackground_966 = null;
		aBackground_967 = null;
		aBackgroundArray1152s = null;
		anIntArray850 = null;
		anIntArray851 = null;
		anIntArray852 = null;
		anIntArray853 = null;
		anIntArray1190 = null;
		anIntArray1191 = null;
		anIntArray828 = null;
		anIntArray829 = null;
		aClass30_Sub2_Sub1_Sub1_1201 = null;
		aClass30_Sub2_Sub1_Sub1_1202 = null;
	}

	private boolean animateRSInterface(int i, int j) {
		boolean flag1 = false;
		Widget class9 = Widget.interfaceCache[j];
		for (int k = 0; k < class9.children.length; k++) {
			if (class9.children[k] == -1)
				break;
			Widget class9_1 = Widget.interfaceCache[class9.children[k]];
			if (class9_1.type == 1)
				flag1 |= animateRSInterface(i, class9_1.id);
			if (class9_1.type == 6
					&& (class9_1.anInt257 != -1 || class9_1.anInt258 != -1)) {
				boolean flag2 = interfaceIsSelected(class9_1);
				int l;
				if (flag2)
					l = class9_1.anInt258;
				else
					l = class9_1.anInt257;
				if (l != -1) {
					Animation animation = Animation.animations[l];
					for (class9_1.anInt208 += i; class9_1.anInt208 > animation
							.method258(class9_1.anInt246);) {
						class9_1.anInt208 -= animation
								.method258(class9_1.anInt246) + 1;
						class9_1.anInt246++;
						if (class9_1.anInt246 >= animation.anInt352) {
							class9_1.anInt246 -= animation.anInt356;
							if (class9_1.anInt246 < 0
									|| class9_1.anInt246 >= animation.anInt352)
								class9_1.anInt246 = 0;
						}
						flag1 = true;
					}

				}
			}
		}

		return flag1;
	}

	private int setCameraLocation() {
		if (!Configuration.enableRoofs)
			return plane;
		int j = 3;
		if (yCameraCurve < 310) {
			int k = xCameraPos >> 7;
			int l = yCameraPos >> 7;
			int i1 = localPlayer.x >> 7;
			int j1 = localPlayer.y >> 7;
			if ((byteGroundArray[plane][k][l] & 4) != 0)
				j = plane;
			int k1;
			if (i1 > k)
				k1 = i1 - k;
			else
				k1 = k - i1;
			int l1;
			if (j1 > l)
				l1 = j1 - l;
			else
				l1 = l - j1;
			if (k1 > l1) {
				int i2 = (l1 * 0x10000) / k1;
				int k2 = 32768;
				while (k != i1) {
					if (k < i1)
						k++;
					else if (k > i1)
						k--;
					if ((byteGroundArray[plane][k][l] & 4) != 0)
						j = plane;
					k2 += i2;
					if (k2 >= 0x10000) {
						k2 -= 0x10000;
						if (l < j1)
							l++;
						else if (l > j1)
							l--;
						if ((byteGroundArray[plane][k][l] & 4) != 0)
							j = plane;
					}
				}
			} else {
				int j2 = (k1 * 0x10000) / l1;
				int l2 = 32768;
				while (l != j1) {
					if (l < j1)
						l++;
					else if (l > j1)
						l--;
					if ((byteGroundArray[plane][k][l] & 4) != 0)
						j = plane;
					l2 += j2;
					if (l2 >= 0x10000) {
						l2 -= 0x10000;
						if (k < i1)
							k++;
						else if (k > i1)
							k--;
						if ((byteGroundArray[plane][k][l] & 4) != 0)
							j = plane;
					}
				}
			}
		}
		if ((byteGroundArray[plane][localPlayer.x >> 7][localPlayer.y >> 7] & 4) != 0)
			j = plane;
		return j;
	}

	private int resetCameraHeight() {
		int j = method42(plane, yCameraPos, xCameraPos);
		if (j - zCameraPos < 800
				&& (byteGroundArray[plane][xCameraPos >> 7][yCameraPos >> 7] & 4) != 0)
			return plane;
		else
			return 3;
	}

	private void delIgnore(long l) {
		try {
			if (l == 0L)
				return;
			for (int j = 0; j < ignoreCount; j++)
				if (ignoreListAsLongs[j] == l) {
					ignoreCount--;
					System.arraycopy(ignoreListAsLongs, j + 1,
							ignoreListAsLongs, j, ignoreCount - j);

					outgoing.createFrame(74);
					outgoing.writeLong(l);
					return;
				}

			return;
		} catch (RuntimeException runtimeexception) {
			Signlink.reporterror("47229, " + 3 + ", " + l + ", "
					+ runtimeexception.toString());
		}
		throw new RuntimeException();
	}

	private void chatJoin(long l) {
		try {
			if (l == 0L)
				return;
			outgoing.createFrame(60);
			outgoing.writeLong(l);
			return;
		} catch (RuntimeException runtimeexception) {
			Signlink.reporterror("47229, " + 3 + ", " + l + ", "
					+ runtimeexception.toString());
		}
		throw new RuntimeException();

	}

	public String getParameter(String s) {
		if (Signlink.mainapp != null)
			return Signlink.mainapp.getParameter(s);
		else
			return super.getParameter(s);
	}

	private int parseInterfaceOpcode(Widget class9, int j) {
		if (class9.scripts == null || j >= class9.scripts.length)
			return -2;
		try {
			int ai[] = class9.scripts[j];
			int k = 0;
			int l = 0;
			int i1 = 0;
			do {
				int j1 = ai[l++];
				int k1 = 0;
				byte byte0 = 0;
				if (j1 == 0)
					return k;
				if (j1 == 1)
					k1 = currentStats[ai[l++]];
				if (j1 == 2)
					k1 = maxStats[ai[l++]];
				if (j1 == 3)
					k1 = currentExp[ai[l++]];
				if (j1 == 4) {
					Widget class9_1 = Widget.interfaceCache[ai[l++]];
					int k2 = ai[l++];
					if (k2 >= 0
							&& k2 < ItemDefinition.item_count
							&& (!ItemDefinition.lookup(k2).is_members_only || isMembers)) {
						for (int j3 = 0; j3 < class9_1.inventoryItemId.length; j3++)
							if (class9_1.inventoryItemId[j3] == k2 + 1)
								k1 += class9_1.invStackSizes[j3];

					}
				}
				if (j1 == 5)
					k1 = variousSettings[ai[l++]];
				if (j1 == 6)
					k1 = anIntArray1019[maxStats[ai[l++]] - 1];
				if (j1 == 7)
					k1 = (variousSettings[ai[l++]] * 100) / 46875;
				if (j1 == 8)
					k1 = localPlayer.combatLevel;
				if (j1 == 9) {
					for (int l1 = 0; l1 < SkillConstants.skillsCount; l1++)
						if (SkillConstants.skillEnabled[l1])
							k1 += maxStats[l1];

				}
				if (j1 == 10) {
					Widget class9_2 = Widget.interfaceCache[ai[l++]];
					int l2 = ai[l++] + 1;
					if (l2 >= 0 && l2 < ItemDefinition.item_count && isMembers) {
						for (int k3 = 0; k3 < class9_2.inventoryItemId.length; k3++) {
							if (class9_2.inventoryItemId[k3] != l2)
								continue;
							k1 = 0x3b9ac9ff;
							break;
						}

					}
				}
				if (j1 == 11)
					k1 = energy;
				if (j1 == 12)
					k1 = weight;
				if (j1 == 13) {
					int i2 = variousSettings[ai[l++]];
					int i3 = ai[l++];
					k1 = (i2 & 1 << i3) == 0 ? 0 : 1;
				}
				if (j1 == 14) {
					int j2 = ai[l++];
					VariableBits varBit = VariableBits.cache[j2];
					int l3 = varBit.getSetting();
					int i4 = varBit.getLow();
					int j4 = varBit.getHigh();
					int k4 = BIT_MASKS[j4 - i4];
					k1 = variousSettings[l3] >> i4 & k4;
				}
				if (j1 == 15)
					byte0 = 1;
				if (j1 == 16)
					byte0 = 2;
				if (j1 == 17)
					byte0 = 3;
				if (j1 == 18)
					k1 = (localPlayer.x >> 7) + baseX;
				if (j1 == 19)
					k1 = (localPlayer.y >> 7) + baseY;
				if (j1 == 20)
					k1 = ai[l++];
				if (byte0 == 0) {
					if (i1 == 0)
						k += k1;
					if (i1 == 1)
						k -= k1;
					if (i1 == 2 && k1 != 0)
						k /= k1;
					if (i1 == 3)
						k *= k1;
					i1 = 0;
				} else {
					i1 = byte0;
				}
			} while (true);
		} catch (Exception _ex) {
			return -1;
		}
	}

	private void drawTooltip() {
		if (menuActionRow < 2 && itemSelected == 0 && spellSelected == 0)
			return;
		String s;
		if (itemSelected == 1 && menuActionRow < 2)
			s = "Use " + selectedItemName + " with...";
		else if (spellSelected == 1 && menuActionRow < 2)
			s = spellTooltip + "...";
		else
			s = menuActionName[menuActionRow - 1];
		if (menuActionRow > 2)
			s = s + "@whi@ / " + (menuActionRow - 2) + " more options";
		boldText.method390(4, 0xffffff, s, loopCycle / 1000, 15);
	}

	private void markMinimap(Sprite sprite, int x, int y) {
		if (sprite == null) {
			return;
		}
		int angle = cameraHorizontal + minimapRotation & 0x7ff;
		int l = x * x + y * y;
		if (l > 6400) {
			return;
		}
		int sineAngle = Model.SINE[angle];
		int cosineAngle = Model.COSINE[angle];
		sineAngle = (sineAngle * 256) / (minimapZoom + 256);
		cosineAngle = (cosineAngle * 256) / (minimapZoom + 256);
		int spriteOffsetX = y * sineAngle + x * cosineAngle >> 16;
		int spriteOffsetY = y * cosineAngle - x * sineAngle >> 16;
		if (frameMode == ScreenMode.FIXED) {
			sprite.drawSprite(
					((94 + spriteOffsetX) - sprite.maxWidth / 2) + 4 + 30, 83
							- spriteOffsetY - sprite.maxHeight / 2 - 4 + 5);
		} else {
			sprite.drawSprite(((77 + spriteOffsetX) - sprite.maxWidth / 2) + 4
					+ 5 + (frameWidth - 167), 85 - spriteOffsetY
					- sprite.maxHeight / 2);
		}
	}

	private void drawMinimap() {
		if (frameMode == ScreenMode.FIXED) {
			minimapImageProducer.initDrawingArea();
		}
		if (minimapState == 2) {
			if (frameMode == ScreenMode.FIXED) {
				cacheSprite[19].drawSprite(0, 0);
			} else {
				cacheSprite[44].drawSprite(frameWidth - 181, 0);
				cacheSprite[45].drawSprite(frameWidth - 158, 7);
			}
			if (frameMode == ScreenMode.FIXED ? super.mouseX >= 519
					&& super.mouseX <= 536 && super.mouseY >= 22
					&& super.mouseY <= 41 : super.mouseX >= frameWidth - 185
					&& super.mouseX <= frameWidth - 158 && super.mouseY >= 40
					&& super.mouseY <= 66) {
				cacheSprite[23].drawSprite(
						Configuration.enableOrbs
								&& frameMode == ScreenMode.FIXED ? 0
								: frameWidth - 185,
						frameMode == ScreenMode.FIXED ? 21 : 41);
			} else {
				cacheSprite[22].drawSprite(
						Configuration.enableOrbs
								&& frameMode == ScreenMode.FIXED ? 0
								: frameWidth - 185,
						frameMode == ScreenMode.FIXED ? 21 : 41);
			}
			if (frameMode != ScreenMode.FIXED && changeTabArea) {
				if (super.mouseX >= frameWidth - 26
						&& super.mouseX <= frameWidth - 1 && super.mouseY >= 2
						&& super.mouseY <= 24 || tabID == 15) {
					cacheSprite[27].drawSprite(frameWidth - 25, 2);
				} else {
					cacheSprite[27].drawARGBSprite(frameWidth - 25, 2, 165);
				}
			}
			loadAllOrbs(frameMode == ScreenMode.FIXED ? 0 : frameWidth - 217);
			compass.rotate(33, cameraHorizontal, anIntArray1057, 256,
					anIntArray968, (frameMode == ScreenMode.FIXED ? 25 : 24),
					4, (frameMode == ScreenMode.FIXED ? 29 : frameWidth - 176),
					33, 25);
			if (menuOpen) {
				drawMenu(frameMode == ScreenMode.FIXED ? 516 : 0, 0);
			}
			if (frameMode == ScreenMode.FIXED) {
				minimapImageProducer.initDrawingArea();
			}
			return;
		}
		int angle = cameraHorizontal + minimapRotation & 0x7ff;
		int centreX = 48 + localPlayer.x / 32;
		int centreY = 464 - localPlayer.y / 32;
		minimapImage.rotate(151, angle, minimapLineWidth, 256 + minimapZoom,
				minimapLeft, centreY, (frameMode == ScreenMode.FIXED ? 9 : 7),
				(frameMode == ScreenMode.FIXED ? 54 : frameWidth - 158), 146,
				centreX);
		for (int icon = 0; icon < anInt1071; icon++) {
			int mapX = (minimapHintX[icon] * 4 + 2) - localPlayer.x / 32;
			int mapY = (minimapHintY[icon] * 4 + 2) - localPlayer.y / 32;
			markMinimap(minimapHint[icon], mapX, mapY);
		}
		for (int x = 0; x < 104; x++) {
			for (int y = 0; y < 104; y++) {
				Deque class19 = groundItems[plane][x][y];
				if (class19 != null) {
					int mapX = (x * 4 + 2) - localPlayer.x / 32;
					int mapY = (y * 4 + 2) - localPlayer.y / 32;
					markMinimap(mapDotItem, mapX, mapY);
				}
			}
		}
		for (int n = 0; n < npcCount; n++) {
			Npc npc = npcs[npcIndices[n]];
			if (npc != null && npc.isVisible()) {
				NpcDefinition entityDef = npc.desc;
				if (entityDef.childrenIDs != null) {
					entityDef = entityDef.morph();
				}
				if (entityDef != null && entityDef.drawMinimapDot
						&& entityDef.clickable) {
					int mapX = npc.x / 32 - localPlayer.x / 32;
					int mapY = npc.y / 32 - localPlayer.y / 32;
					markMinimap(mapDotNPC, mapX, mapY);
				}
			}
		}
		for (int p = 0; p < playerCount; p++) {
			Player player = players[playerIndices[p]];
			if (player != null && player.isVisible()) {
				int mapX = player.x / 32 - localPlayer.x / 32;
				int mapY = player.y / 32 - localPlayer.y / 32;
				boolean friend = false;
				boolean clanMember = false;
				for (int clan = 0; clan < clanList.length; clan++) {
					if (clanList[clan] == null) {
						continue;
					}
					if (!clanList[clan].equalsIgnoreCase(player.name)) {
						continue;
					}
					clanMember = true;
					break;
				}
				long nameHash = TextClass.longForName(player.name);
				for (int f = 0; f < friendsCount; f++) {
					if (nameHash != friendsListAsLongs[f]
							|| friendsNodeIDs[f] == 0) {
						continue;
					}
					friend = true;
					break;
				}
				boolean team = false;
				if (localPlayer.team != 0 && player.team != 0
						&& localPlayer.team == player.team) {
					team = true;
				}
				if (friend) {
					markMinimap(mapDotFriend, mapX, mapY);
				} else if (clanMember) {
					markMinimap(mapDotClan, mapX, mapY);
				} else if (team) {
					markMinimap(mapDotTeam, mapX, mapY);
				} else {
					markMinimap(mapDotPlayer, mapX, mapY);
				}
			}
		}
		if (hintIconDrawType != 0 && loopCycle % 20 < 10) {
			if (hintIconDrawType == 1 && hintIconNpcId >= 0
					&& hintIconNpcId < npcs.length) {
				Npc npc = npcs[hintIconNpcId];
				if (npc != null) {
					int mapX = npc.x / 32 - localPlayer.x / 32;
					int mapY = npc.y / 32 - localPlayer.y / 32;
					refreshMinimap(mapMarker, mapY, mapX);
				}
			}
			if (hintIconDrawType == 2) {
				int mapX = ((hintIconX - baseX) * 4 + 2) - localPlayer.x / 32;
				int mapY = ((hintIconY - baseY) * 4 + 2) - localPlayer.y / 32;
				refreshMinimap(mapMarker, mapY, mapX);
			}
			if (hintIconDrawType == 10 && hintIconPlayerId >= 0
					&& hintIconPlayerId < players.length) {
				Player player = players[hintIconPlayerId];
				if (player != null) {
					int mapX = player.x / 32 - localPlayer.x / 32;
					int mapY = player.y / 32 - localPlayer.y / 32;
					refreshMinimap(mapMarker, mapY, mapX);
				}
			}
		}
		if (destinationX != 0) {
			int mapX = (destinationX * 4 + 2) - localPlayer.x / 32;
			int mapY = (destY * 4 + 2) - localPlayer.y / 32;
			markMinimap(mapFlag, mapX, mapY);
		}
		Raster.drawPixels(3, (frameMode == ScreenMode.FIXED ? 83 : 80),
				(frameMode == ScreenMode.FIXED ? 127 : frameWidth - 88),
				0xffffff, 3);
		if (frameMode == ScreenMode.FIXED) {
			cacheSprite[19].drawSprite(0, 0);
		} else {
			cacheSprite[44].drawSprite(frameWidth - 181, 0);
		}
		compass.rotate(33, cameraHorizontal, anIntArray1057, 256,
				anIntArray968, (frameMode == ScreenMode.FIXED ? 25 : 24), 4,
				(frameMode == ScreenMode.FIXED ? 29 : frameWidth - 176), 33, 25);
		if (frameMode == ScreenMode.FIXED ? super.mouseX >= 519
				&& super.mouseX <= 536 && super.mouseY >= 22
				&& super.mouseY <= 41 : super.mouseX >= frameWidth - 185
				&& super.mouseX <= frameWidth - 158 && super.mouseY >= 40
				&& super.mouseY <= 66) {
			cacheSprite[23].drawSprite(Configuration.enableOrbs
					&& frameMode == ScreenMode.FIXED ? 0 : frameWidth - 185,
					frameMode == ScreenMode.FIXED ? 21 : 41);
		} else {
			cacheSprite[22].drawSprite(Configuration.enableOrbs
					&& frameMode == ScreenMode.FIXED ? 0 : frameWidth - 185,
					frameMode == ScreenMode.FIXED ? 21 : 41);
		}
		if (frameMode != ScreenMode.FIXED && changeTabArea) {
			if (super.mouseX >= frameWidth - 26
					&& super.mouseX <= frameWidth - 1 && super.mouseY >= 2
					&& super.mouseY <= 24 || tabID == 10) {
				cacheSprite[27].drawSprite(frameWidth - 25, 2);
			} else {
				cacheSprite[27].drawARGBSprite(frameWidth - 25, 2, 165);
			}
		}
		loadAllOrbs(frameMode == ScreenMode.FIXED ? 0 : frameWidth - 217);
		if (menuOpen) {
			drawMenu(frameMode == ScreenMode.FIXED ? 516 : 0, 0);
		}
		if (frameMode == ScreenMode.FIXED) {
			gameScreenImageProducer.initDrawingArea();
		}
	}

	private void npcScreenPos(Entity entity, int i) {
		calcEntityScreenPos(entity.x, i, entity.y);
	}

	private void calcEntityScreenPos(int i, int j, int l) {
		if (i < 128 || l < 128 || i > 13056 || l > 13056) {
			spriteDrawX = -1;
			spriteDrawY = -1;
			return;
		}
		int i1 = method42(plane, l, i) - j;
		i -= xCameraPos;
		i1 -= zCameraPos;
		l -= yCameraPos;
		int j1 = Model.SINE[yCameraCurve];
		int k1 = Model.COSINE[yCameraCurve];
		int l1 = Model.SINE[xCameraCurve];
		int i2 = Model.COSINE[xCameraCurve];
		int j2 = l * l1 + i * i2 >> 16;
		l = l * i2 - i * l1 >> 16;
		i = j2;
		j2 = i1 * k1 - l * j1 >> 16;
		l = i1 * j1 + l * k1 >> 16;
		i1 = j2;
		if (l >= 50) {
			spriteDrawX = Rasterizer.textureInt1
					+ (i << SceneGraph.viewDistance) / l;
			spriteDrawY = Rasterizer.textureInt2
					+ (i1 << SceneGraph.viewDistance) / l;
		} else {
			spriteDrawX = -1;
			spriteDrawY = -1;
		}
	}

	private void buildSplitPrivateChatMenu() {
		if (splitPrivateChat == 0)
			return;
		int i = 0;
		if (systemUpdateTime != 0)
			i = 1;
		for (int j = 0; j < 100; j++)
			if (chatMessages[j] != null) {
				int k = chatTypes[j];
				String s = chatNames[j];
				if (s != null && s.startsWith("@cr1@")) {
					s = s.substring(5);
				}
				if (s != null && s.startsWith("@cr2@")) {
					s = s.substring(5);
				}
				if ((k == 3 || k == 7)
						&& (k == 7 || privateChatMode == 0 || privateChatMode == 1
								&& isFriendOrSelf(s))) {
					int offSet = frameMode == ScreenMode.FIXED ? 4 : 0;
					int l = 329 - i * 13;
					if (frameMode != ScreenMode.FIXED) {
						l = frameHeight - 170 - i * 13;
					}
					if (super.mouseX > 4 && super.mouseY - offSet > l - 10
							&& super.mouseY - offSet <= l + 3) {
						int i1 = regularText.getTextWidth("From:  " + s
								+ chatMessages[j]) + 25;
						if (i1 > 450)
							i1 = 450;
						if (super.mouseX < 4 + i1) {
							if (myPrivilege >= 1) {
								menuActionName[menuActionRow] = "Report abuse @whi@"
										+ s;
								menuActionID[menuActionRow] = 2606;
								menuActionRow++;
							}
							menuActionName[menuActionRow] = "Add ignore @whi@"
									+ s;
							menuActionID[menuActionRow] = 2042;
							menuActionRow++;
							menuActionName[menuActionRow] = "Add friend @whi@"
									+ s;
							menuActionID[menuActionRow] = 2337;
							menuActionRow++;
						}
					}
					if (++i >= 5)
						return;
				}
				if ((k == 5 || k == 6) && privateChatMode < 2 && ++i >= 5)
					return;
			}

	}

	private void method130(int j, int k, int l, int i1, int j1, int k1, int l1,
			int i2, int j2) {
		TemporaryObject class30_sub1 = null;
		for (TemporaryObject class30_sub1_1 = (TemporaryObject) spawns
				.reverseGetFirst(); class30_sub1_1 != null; class30_sub1_1 = (TemporaryObject) spawns
				.reverseGetNext()) {
			if (class30_sub1_1.anInt1295 != l1
					|| class30_sub1_1.anInt1297 != i2
					|| class30_sub1_1.anInt1298 != j1
					|| class30_sub1_1.anInt1296 != i1)
				continue;
			class30_sub1 = class30_sub1_1;
			break;
		}

		if (class30_sub1 == null) {
			class30_sub1 = new TemporaryObject();
			class30_sub1.anInt1295 = l1;
			class30_sub1.anInt1296 = i1;
			class30_sub1.anInt1297 = i2;
			class30_sub1.anInt1298 = j1;
			method89(class30_sub1);
			spawns.insertHead(class30_sub1);
		}
		class30_sub1.anInt1291 = k;
		class30_sub1.anInt1293 = k1;
		class30_sub1.anInt1292 = l;
		class30_sub1.anInt1302 = j2;
		class30_sub1.anInt1294 = j;
	}

	private boolean interfaceIsSelected(Widget class9) {
		if (class9.scriptOperators == null)
			return false;
		for (int i = 0; i < class9.scriptOperators.length; i++) {
			int j = parseInterfaceOpcode(class9, i);
			int k = class9.scriptDefaults[i];
			if (class9.scriptOperators[i] == 2) {
				if (j >= k)
					return false;
			} else if (class9.scriptOperators[i] == 3) {
				if (j <= k)
					return false;
			} else if (class9.scriptOperators[i] == 4) {
				if (j == k)
					return false;
			} else if (j != k)
				return false;
		}

		return true;
	}

	private DataInputStream openJagGrabInputStream(String s) throws IOException {
		// if(!aBoolean872)
		// if(signlink.mainapp != null)
		// return signlink.openurl(s);
		// else
		// return new DataInputStream((new URL(getCodeBase(), s)).openStream());
		if (aSocket832 != null) {
			try {
				aSocket832.close();
			} catch (Exception _ex) {
			}
			aSocket832 = null;
		}
		aSocket832 = openSocket(43595);
		aSocket832.setSoTimeout(10000);
		java.io.InputStream inputstream = aSocket832.getInputStream();
		OutputStream outputstream = aSocket832.getOutputStream();
		outputstream.write(("JAGGRAB /" + s + "\n\n").getBytes());
		return new DataInputStream(inputstream);
	}

	private void doFlamesDrawing() {
		char c = '\u0100';
		if (anInt1040 > 0) {
			for (int i = 0; i < 256; i++)
				if (anInt1040 > 768)
					anIntArray850[i] = method83(anIntArray851[i],
							anIntArray852[i], 1024 - anInt1040);
				else if (anInt1040 > 256)
					anIntArray850[i] = anIntArray852[i];
				else
					anIntArray850[i] = method83(anIntArray852[i],
							anIntArray851[i], 256 - anInt1040);

		} else if (anInt1041 > 0) {
			for (int j = 0; j < 256; j++)
				if (anInt1041 > 768)
					anIntArray850[j] = method83(anIntArray851[j],
							anIntArray853[j], 1024 - anInt1041);
				else if (anInt1041 > 256)
					anIntArray850[j] = anIntArray853[j];
				else
					anIntArray850[j] = method83(anIntArray853[j],
							anIntArray851[j], 256 - anInt1041);

		} else {
			System.arraycopy(anIntArray851, 0, anIntArray850, 0, 256);

		}
		System.arraycopy(aClass30_Sub2_Sub1_Sub1_1201.myPixels, 0,
				flameLeftBackground.canvasRaster, 0, 33920);

		int i1 = 0;
		int j1 = 1152;
		for (int k1 = 1; k1 < c - 1; k1++) {
			int l1 = (anIntArray969[k1] * (c - k1)) / c;
			int j2 = 22 + l1;
			if (j2 < 0)
				j2 = 0;
			i1 += j2;
			for (int l2 = j2; l2 < 128; l2++) {
				int j3 = anIntArray828[i1++];
				if (j3 != 0) {
					int l3 = j3;
					int j4 = 256 - j3;
					j3 = anIntArray850[j3];
					int l4 = flameLeftBackground.canvasRaster[j1];
					flameLeftBackground.canvasRaster[j1++] = ((j3 & 0xff00ff)
							* l3 + (l4 & 0xff00ff) * j4 & 0xff00ff00)
							+ ((j3 & 0xff00) * l3 + (l4 & 0xff00) * j4 & 0xff0000) >> 8;
				} else {
					j1++;
				}
			}

			j1 += j2;
		}

		flameLeftBackground.drawGraphics(0, super.graphics, 0);
		System.arraycopy(aClass30_Sub2_Sub1_Sub1_1202.myPixels, 0,
				flameRightBackground.canvasRaster, 0, 33920);

		i1 = 0;
		j1 = 1176;
		for (int k2 = 1; k2 < c - 1; k2++) {
			int i3 = (anIntArray969[k2] * (c - k2)) / c;
			int k3 = 103 - i3;
			j1 += i3;
			for (int i4 = 0; i4 < k3; i4++) {
				int k4 = anIntArray828[i1++];
				if (k4 != 0) {
					int i5 = k4;
					int j5 = 256 - k4;
					k4 = anIntArray850[k4];
					int k5 = flameRightBackground.canvasRaster[j1];
					flameRightBackground.canvasRaster[j1++] = ((k4 & 0xff00ff)
							* i5 + (k5 & 0xff00ff) * j5 & 0xff00ff00)
							+ ((k4 & 0xff00) * i5 + (k5 & 0xff00) * j5 & 0xff0000) >> 8;
				} else {
					j1++;
				}
			}

			i1 += 128 - k3;
			j1 += 128 - k3 - i3;
		}

		flameRightBackground.drawGraphics(0, super.graphics, 637);
	}

	private void method134(Buffer stream) {
		int j = stream.readBits(8);
		if (j < playerCount) {
			for (int k = j; k < playerCount; k++)
				anIntArray840[anInt839++] = playerIndices[k];

		}
		if (j > playerCount) {
			Signlink.reporterror(myUsername + " Too many players");
			throw new RuntimeException("eek");
		}
		playerCount = 0;
		for (int l = 0; l < j; l++) {
			int i1 = playerIndices[l];
			Player player = players[i1];
			int j1 = stream.readBits(1);
			if (j1 == 0) {
				playerIndices[playerCount++] = i1;
				player.anInt1537 = loopCycle;
			} else {
				int k1 = stream.readBits(2);
				if (k1 == 0) {
					playerIndices[playerCount++] = i1;
					player.anInt1537 = loopCycle;
					anIntArray894[anInt893++] = i1;
				} else if (k1 == 1) {
					playerIndices[playerCount++] = i1;
					player.anInt1537 = loopCycle;
					int l1 = stream.readBits(3);
					player.moveInDir(false, l1);
					int j2 = stream.readBits(1);
					if (j2 == 1)
						anIntArray894[anInt893++] = i1;
				} else if (k1 == 2) {
					playerIndices[playerCount++] = i1;
					player.anInt1537 = loopCycle;
					int i2 = stream.readBits(3);
					player.moveInDir(true, i2);
					int k2 = stream.readBits(3);
					player.moveInDir(true, k2);
					int l2 = stream.readBits(1);
					if (l2 == 1)
						anIntArray894[anInt893++] = i1;
				} else if (k1 == 3)
					anIntArray840[anInt839++] = i1;
			}
		}
	}

	private ImageProducer loginScreenAccessories;

	public void loginScreenAccessories() {
		/**
		 * World-selection
		 */
		setupLoginScreen();

		loginScreenAccessories.drawGraphics(400, super.graphics, 0);
		loginScreenAccessories.initDrawingArea();
		cacheSprite[57].drawSprite(6, 63);
		if (!Configuration.worldSwitch) {
			boldText.method382(0xffffff, 55, "World 301", 78, true);
			smallText.method382(0xffffff, 55, "Click to switch", 92, true);
			Configuration.server_address = "localhost";
			Configuration.server_port = 43594;
		} else {
			boldText.method382(0xffffff, 55, "World 302", 78, true);
			smallText.method382(0xffffff, 55, "Click to switch", 92, true);
			Configuration.server_address = "localhost";
			Configuration.server_port = 5555;
		}

		loginMusicImageProducer.drawGraphics(265, super.graphics, 562);
		loginMusicImageProducer.initDrawingArea();
		if (Configuration.enableMusic) {
			cacheSprite[58].drawSprite(158, 196);
		} else {
			cacheSprite[59].drawSprite(158, 196);
			stopMidi();
		}

	}

	public void drawMusicSprites() {

		int musicState = 0;
		bottomRightImageProducer.initDrawingArea();
		switch (musicState) {
		case 0:
			cacheSprite[58].drawSprite(158, 196);
			break;

		case 1:
			cacheSprite[59].drawSprite(158, 196);
			break;
		}
	}

	private void drawLoginScreen(boolean flag) {
		setupLoginScreen();
		loginBoxImageProducer.initDrawingArea();
		aBackground_966.drawBackground(0, 0);
		// regularText.method385(0xffffff, "Mouse X: " + super.mouseX +
		// " , Mouse Y: " + super.mouseY, 30, frameMode == ScreenMode.FIXED ? 5
		// : frameWidth - 5);
		char c = '\u0168';
		char c1 = '\310';
		if (Configuration.enableMusic && !lowMem) {
			playSong(SoundConstants.SCAPE_RUNE);
		}
		if (loginScreenState == 0) {
			int i = c1 / 2 + 80;
			smallText.method382(0x75a9a9, c / 2, onDemandFetcher.statusString,
					i, true);
			i = c1 / 2 - 20;
			boldText.method382(0xffff00, c / 2, "Welcome to "
					+ Configuration.CLIENT_NAME, i, true);
			i += 30;
			int l = c / 2 - 80;
			int k1 = c1 / 2 + 20;
			aBackground_967.drawBackground(l - 73, k1 - 20);
			boldText.method382(0xffffff, l, "New User", k1 + 5, true);
			l = c / 2 + 80;
			aBackground_967.drawBackground(l - 73, k1 - 20);
			boldText.method382(0xffffff, l, "Existing User", k1 + 5, true);
		}
		if (loginScreenState == 2) {
			int j = c1 / 2 - 40;
			if (firstLoginMessage.length() > 0) {
				boldText.method382(0xffff00, c / 2, firstLoginMessage, j - 15,
						true);
				boldText.method382(0xffff00, c / 2, secondLoginMessage, j, true);
				j += 30;
			} else {
				boldText.method382(0xffff00, c / 2, secondLoginMessage, j - 7,
						true);
				j += 30;
			}
			boldText.drawTextWithPotentialShadow(true, c / 2 - 90, 0xffffff,
					"Login: "
							+ myUsername
							+ ((loginScreenCursorPos == 0)
									& (loopCycle % 40 < 20) ? "@yel@|" : ""), j);
			j += 15;
			boldText.drawTextWithPotentialShadow(true, c / 2 - 88, 0xffffff,
					"Password: "
							+ TextClass.passwordAsterisks(myPassword)
							+ ((loginScreenCursorPos == 1)
									& (loopCycle % 40 < 20) ? "@yel@|" : ""), j);
			j += 15;
			if (!flag) {
				int i1 = c / 2 - 80;
				int l1 = c1 / 2 + 50;
				aBackground_967.drawBackground(i1 - 73, l1 - 20);
				boldText.method382(0xffffff, i1, "Login", l1 + 5, true);
				i1 = c / 2 + 80;
				aBackground_967.drawBackground(i1 - 73, l1 - 20);
				boldText.method382(0xffffff, i1, "Cancel", l1 + 5, true);
			}
		}
		if (loginScreenState == 3) {
			loginScreenState = 0;
			launchURL(Configuration.REGISTER_ACCOUNT);
		}
		loginBoxImageProducer.drawGraphics(171, super.graphics, 202);
		if (welcomeScreenRaised) {
			welcomeScreenRaised = false;
			topLeft1BackgroundTile.drawGraphics(0, super.graphics, 128);
			bottomLeft1BackgroundTile.drawGraphics(371, super.graphics, 202);
			bottomLeft0BackgroundTile.drawGraphics(265, super.graphics, 0);
			bottomRightImageProducer.drawGraphics(265, super.graphics, 562);
			middleLeft1BackgroundTile.drawGraphics(171, super.graphics, 128);
			aRSImageProducer_1115.drawGraphics(171, super.graphics, 562);
		}
		loginScreenAccessories();
	}

	private void drawFlames() {
		drawingFlames = true;
		try {
			long l = System.currentTimeMillis();
			int i = 0;
			int j = 20;
			while (aBoolean831) {
				calcFlamesPosition();
				calcFlamesPosition();
				doFlamesDrawing();
				if (++i > 10) {
					long l1 = System.currentTimeMillis();
					int k = (int) (l1 - l) / 10 - j;
					j = 40 - k;
					if (j < 5)
						j = 5;
					i = 0;
					l = l1;
				}
				try {
					Thread.sleep(j);
				} catch (Exception _ex) {
				}
			}
		} catch (Exception _ex) {
		}
		drawingFlames = false;
	}

	public void raiseWelcomeScreen() {
		welcomeScreenRaised = true;
	}

	private void method137(Buffer stream, int j) {
		if (j == 84) {
			int k = stream.readUnsignedByte();
			int j3 = anInt1268 + (k >> 4 & 7);
			int i6 = anInt1269 + (k & 7);
			int l8 = stream.readUShort();
			int k11 = stream.readUShort();
			int l13 = stream.readUShort();
			if (j3 >= 0 && i6 >= 0 && j3 < 104 && i6 < 104) {
				Deque class19_1 = groundItems[plane][j3][i6];
				if (class19_1 != null) {
					for (Item class30_sub2_sub4_sub2_3 = (Item) class19_1
							.reverseGetFirst(); class30_sub2_sub4_sub2_3 != null; class30_sub2_sub4_sub2_3 = (Item) class19_1
							.reverseGetNext()) {
						if (class30_sub2_sub4_sub2_3.ID != (l8 & 0x7fff)
								|| class30_sub2_sub4_sub2_3.anInt1559 != k11)
							continue;
						class30_sub2_sub4_sub2_3.anInt1559 = l13;
						break;
					}

					spawnGroundItem(j3, i6);
				}
			}
			return;
		}
		if (j == 105) {
			int l = stream.readUnsignedByte();
			int k3 = anInt1268 + (l >> 4 & 7);
			int j6 = anInt1269 + (l & 7);
			int i9 = stream.readUShort();
			int l11 = stream.readUnsignedByte();
			int i14 = l11 >> 4 & 0xf;
			int i16 = l11 & 7;
			if (localPlayer.pathX[0] >= k3 - i14
					&& localPlayer.pathX[0] <= k3 + i14
					&& localPlayer.pathY[0] >= j6 - i14
					&& localPlayer.pathY[0] <= j6 + i14 && aBoolean848
					&& !lowMem && trackCount < 50) {
				tracks[trackCount] = i9;
				trackLoops[trackCount] = i16;
				soundDelay[trackCount] = SoundTrack.delays[i9];
				trackCount++;
			}
		}
		if (j == 215) {
			int i1 = stream.readUShortA();
			int l3 = stream.readUByteS();
			int k6 = anInt1268 + (l3 >> 4 & 7);
			int j9 = anInt1269 + (l3 & 7);
			int i12 = stream.readUShortA();
			int j14 = stream.readUShort();
			if (k6 >= 0 && j9 >= 0 && k6 < 104 && j9 < 104
					&& i12 != unknownInt10) {
				Item class30_sub2_sub4_sub2_2 = new Item();
				class30_sub2_sub4_sub2_2.ID = i1;
				class30_sub2_sub4_sub2_2.anInt1559 = j14;
				if (groundItems[plane][k6][j9] == null)
					groundItems[plane][k6][j9] = new Deque();
				groundItems[plane][k6][j9].insertHead(class30_sub2_sub4_sub2_2);
				spawnGroundItem(k6, j9);
			}
			return;
		}
		if (j == 156) {
			int j1 = stream.readUByteA();
			int i4 = anInt1268 + (j1 >> 4 & 7);
			int l6 = anInt1269 + (j1 & 7);
			int k9 = stream.readUShort();
			if (i4 >= 0 && l6 >= 0 && i4 < 104 && l6 < 104) {
				Deque class19 = groundItems[plane][i4][l6];
				if (class19 != null) {
					for (Item item = (Item) class19.reverseGetFirst(); item != null; item = (Item) class19
							.reverseGetNext()) {
						if (item.ID != (k9 & 0x7fff))
							continue;
						item.unlink();
						break;
					}

					if (class19.reverseGetFirst() == null)
						groundItems[plane][i4][l6] = null;
					spawnGroundItem(i4, l6);
				}
			}
			return;
		}
		if (j == 160) {
			int k1 = stream.readUByteS();
			int j4 = anInt1268 + (k1 >> 4 & 7);
			int i7 = anInt1269 + (k1 & 7);
			int l9 = stream.readUByteS();
			int j12 = l9 >> 2;
			int k14 = l9 & 3;
			int j16 = anIntArray1177[j12];
			int j17 = stream.readUShortA();
			if (j4 >= 0 && i7 >= 0 && j4 < 103 && i7 < 103) {
				int j18 = intGroundArray[plane][j4][i7];
				int i19 = intGroundArray[plane][j4 + 1][i7];
				int l19 = intGroundArray[plane][j4 + 1][i7 + 1];
				int k20 = intGroundArray[plane][j4][i7 + 1];
				if (j16 == 0) {
					WallLock class10 = worldController.method296(plane, j4, i7);
					if (class10 != null) {
						int k21 = class10.uid >> 14 & 0x7fff;
						if (j12 == 2) {
							class10.aClass30_Sub2_Sub4_278 = new SceneObject(
									k21, 4 + k14, 2, i19, l19, j18, k20, j17,
									false);
							class10.aClass30_Sub2_Sub4_279 = new SceneObject(
									k21, k14 + 1 & 3, 2, i19, l19, j18, k20,
									j17, false);
						} else {
							class10.aClass30_Sub2_Sub4_278 = new SceneObject(
									k21, k14, j12, i19, l19, j18, k20, j17,
									false);
						}
					}
				}
				if (j16 == 1) {
					WallDecoration class26 = worldController.method297(j4, i7,
							plane);
					if (class26 != null)
						class26.aClass30_Sub2_Sub4_504 = new SceneObject(
								class26.uid >> 14 & 0x7fff, 0, 4, i19, l19,
								j18, k20, j17, false);
				}
				if (j16 == 2) {
					StaticObject class28 = worldController.method298(j4, i7,
							plane);
					if (j12 == 11)
						j12 = 10;
					if (class28 != null)
						class28.aClass30_Sub2_Sub4_521 = new SceneObject(
								class28.uid >> 14 & 0x7fff, k14, j12, i19, l19,
								j18, k20, j17, false);
				}
				if (j16 == 3) {
					GroundDecoration class49 = worldController.method299(i7,
							j4, plane);
					if (class49 != null)
						class49.aClass30_Sub2_Sub4_814 = new SceneObject(
								class49.uid >> 14 & 0x7fff, k14, 22, i19, l19,
								j18, k20, j17, false);
				}
			}
			return;
		}
		if (j == 147) {
			int l1 = stream.readUByteS();
			int k4 = anInt1268 + (l1 >> 4 & 7);
			int j7 = anInt1269 + (l1 & 7);
			int i10 = stream.readUShort();
			byte byte0 = stream.readByteS();
			int l14 = stream.readLEUShort();
			byte byte1 = stream.readNegByte();
			int k17 = stream.readUShort();
			int k18 = stream.readUByteS();
			int j19 = k18 >> 2;
			int i20 = k18 & 3;
			int l20 = anIntArray1177[j19];
			byte byte2 = stream.readSignedByte();
			int l21 = stream.readUShort();
			byte byte3 = stream.readNegByte();
			Player player;
			if (i10 == unknownInt10)
				player = localPlayer;
			else
				player = players[i10];
			if (player != null) {
				ObjectDefinition class46 = ObjectDefinition.lookup(l21);
				int i22 = intGroundArray[plane][k4][j7];
				int j22 = intGroundArray[plane][k4 + 1][j7];
				int k22 = intGroundArray[plane][k4 + 1][j7 + 1];
				int l22 = intGroundArray[plane][k4][j7 + 1];
				Model model = class46.modelAt(j19, i20, i22, j22, k22, l22, -1);
				if (model != null) {
					method130(k17 + 1, -1, 0, l20, j7, 0, plane, k4, l14 + 1);
					player.anInt1707 = l14 + loopCycle;
					player.anInt1708 = k17 + loopCycle;
					player.aModel_1714 = model;
					int i23 = class46.width;
					int j23 = class46.length;
					if (i20 == 1 || i20 == 3) {
						i23 = class46.length;
						j23 = class46.width;
					}
					player.anInt1711 = k4 * 128 + i23 * 64;
					player.anInt1713 = j7 * 128 + j23 * 64;
					player.anInt1712 = method42(plane, player.anInt1713,
							player.anInt1711);
					if (byte2 > byte0) {
						byte byte4 = byte2;
						byte2 = byte0;
						byte0 = byte4;
					}
					if (byte3 > byte1) {
						byte byte5 = byte3;
						byte3 = byte1;
						byte1 = byte5;
					}
					player.anInt1719 = k4 + byte2;
					player.anInt1721 = k4 + byte0;
					player.anInt1720 = j7 + byte3;
					player.anInt1722 = j7 + byte1;
				}
			}
		}
		if (j == 151) {
			int i2 = stream.readUByteA();
			int l4 = anInt1268 + (i2 >> 4 & 7);
			int k7 = anInt1269 + (i2 & 7);
			int j10 = stream.readLEUShort();
			int k12 = stream.readUByteS();
			int i15 = k12 >> 2;
			int k16 = k12 & 3;
			int l17 = anIntArray1177[i15];
			if (l4 >= 0 && k7 >= 0 && l4 < 104 && k7 < 104)
				method130(-1, j10, k16, l17, k7, i15, plane, l4, 0);
			return;
		}
		if (j == 4) {
			int j2 = stream.readUnsignedByte();
			int i5 = anInt1268 + (j2 >> 4 & 7);
			int l7 = anInt1269 + (j2 & 7);
			int k10 = stream.readUShort();
			int l12 = stream.readUnsignedByte();
			int j15 = stream.readUShort();
			if (i5 >= 0 && l7 >= 0 && i5 < 104 && l7 < 104) {
				i5 = i5 * 128 + 64;
				l7 = l7 * 128 + 64;
				SceneSpotAnim class30_sub2_sub4_sub3 = new SceneSpotAnim(plane,
						loopCycle, j15, k10, method42(plane, l7, i5) - l12, l7,
						i5);
				incompleteAnimables.insertHead(class30_sub2_sub4_sub3);
			}
			return;
		}
		if (j == 44) {
			int k2 = stream.readLEUShortA();
			int j5 = stream.readUShort();
			int i8 = stream.readUnsignedByte();
			int l10 = anInt1268 + (i8 >> 4 & 7);
			int i13 = anInt1269 + (i8 & 7);
			if (l10 >= 0 && i13 >= 0 && l10 < 104 && i13 < 104) {
				Item class30_sub2_sub4_sub2_1 = new Item();
				class30_sub2_sub4_sub2_1.ID = k2;
				class30_sub2_sub4_sub2_1.anInt1559 = j5;
				if (groundItems[plane][l10][i13] == null)
					groundItems[plane][l10][i13] = new Deque();
				groundItems[plane][l10][i13]
						.insertHead(class30_sub2_sub4_sub2_1);
				spawnGroundItem(l10, i13);
			}
			return;
		}
		if (j == 101) {
			int l2 = stream.readNegUByte();
			int k5 = l2 >> 2;
			int j8 = l2 & 3;
			int i11 = anIntArray1177[k5];
			int j13 = stream.readUnsignedByte();
			int k15 = anInt1268 + (j13 >> 4 & 7);
			int l16 = anInt1269 + (j13 & 7);
			if (k15 >= 0 && l16 >= 0 && k15 < 104 && l16 < 104)
				method130(-1, -1, j8, i11, l16, k5, plane, k15, 0);
			return;
		}
		if (j == 117) {
			int i3 = stream.readUnsignedByte();
			int l5 = anInt1268 + (i3 >> 4 & 7);
			int k8 = anInt1269 + (i3 & 7);
			int j11 = l5 + stream.readSignedByte();
			int k13 = k8 + stream.readSignedByte();
			int l15 = stream.readShort();
			int i17 = stream.readUShort();
			int i18 = stream.readUnsignedByte() * 4;
			int l18 = stream.readUnsignedByte() * 4;
			int k19 = stream.readUShort();
			int j20 = stream.readUShort();
			int i21 = stream.readUnsignedByte();
			int j21 = stream.readUnsignedByte();
			if (l5 >= 0 && k8 >= 0 && l5 < 104 && k8 < 104 && j11 >= 0
					&& k13 >= 0 && j11 < 104 && k13 < 104 && i17 != 65535) {
				l5 = l5 * 128 + 64;
				k8 = k8 * 128 + 64;
				j11 = j11 * 128 + 64;
				k13 = k13 * 128 + 64;
				SceneProjectile class30_sub2_sub4_sub4 = new SceneProjectile(
						i21, l18, k19 + loopCycle, j20 + loopCycle, j21, plane,
						method42(plane, k8, l5) - i18, k8, l5, l15, i17);
				class30_sub2_sub4_sub4.method455(k19 + loopCycle, k13,
						method42(plane, k13, j11) - l18, j11);
				projectiles.insertHead(class30_sub2_sub4_sub4);
			}
		}
	}

	private void method139(Buffer stream) {
		stream.initBitAccess();
		int k = stream.readBits(8);
		if (k < npcCount) {
			for (int l = k; l < npcCount; l++)
				anIntArray840[anInt839++] = npcIndices[l];

		}
		if (k > npcCount) {
			Signlink.reporterror(myUsername + " Too many npcs");
			throw new RuntimeException("eek");
		}
		npcCount = 0;
		for (int i1 = 0; i1 < k; i1++) {
			int j1 = npcIndices[i1];
			Npc npc = npcs[j1];
			int k1 = stream.readBits(1);
			if (k1 == 0) {
				npcIndices[npcCount++] = j1;
				npc.anInt1537 = loopCycle;
			} else {
				int l1 = stream.readBits(2);
				if (l1 == 0) {
					npcIndices[npcCount++] = j1;
					npc.anInt1537 = loopCycle;
					anIntArray894[anInt893++] = j1;
				} else if (l1 == 1) {
					npcIndices[npcCount++] = j1;
					npc.anInt1537 = loopCycle;
					int i2 = stream.readBits(3);
					npc.moveInDir(false, i2);
					int k2 = stream.readBits(1);
					if (k2 == 1)
						anIntArray894[anInt893++] = j1;
				} else if (l1 == 2) {
					npcIndices[npcCount++] = j1;
					npc.anInt1537 = loopCycle;
					int j2 = stream.readBits(3);
					npc.moveInDir(true, j2);
					int l2 = stream.readBits(3);
					npc.moveInDir(true, l2);
					int i3 = stream.readBits(1);
					if (i3 == 1)
						anIntArray894[anInt893++] = j1;
				} else if (l1 == 3)
					anIntArray840[anInt839++] = j1;
			}
		}

	}

	private void processLoginScreenInput() {
		if (loginScreenState == 0) {
			if (super.clickMode3 == 1 && super.saveClickX >= 722
					&& super.saveClickX <= 751 && super.saveClickY >= 463
					&& super.saveClickY <= 493) {
				Configuration.enableMusic = !Configuration.enableMusic;
			}

			if (super.clickMode3 == 1 && super.saveClickX >= 7
					&& super.saveClickX <= 104 && super.saveClickY >= 464
					&& super.saveClickY <= 493) {
				Configuration.worldSwitch = !Configuration.worldSwitch;
			}

			int i = super.myWidth / 2 - 80;
			int l = super.myHeight / 2 + 20;
			l += 20;
			if (super.clickMode3 == 1 && super.saveClickX >= i - 75
					&& super.saveClickX <= i + 75 && super.saveClickY >= l - 20
					&& super.saveClickY <= l + 20) {
				loginScreenState = 3;
				loginScreenCursorPos = 0;
			}
			i = super.myWidth / 2 + 80;
			if (super.clickMode3 == 1 && super.saveClickX >= i - 75
					&& super.saveClickX <= i + 75 && super.saveClickY >= l - 20
					&& super.saveClickY <= l + 20) {
				firstLoginMessage = "";
				secondLoginMessage = "Enter your username & password.";
				loginScreenState = 2;
				loginScreenCursorPos = 0;
			}
		} else if (loginScreenState == 2) {

			if (super.clickMode3 == 1 && super.saveClickX >= 722
					&& super.saveClickX <= 751 && super.saveClickY >= 463
					&& super.saveClickY <= 493) {
				Configuration.enableMusic = !Configuration.enableMusic;
			}
			if (super.clickMode3 == 1 && super.saveClickX >= 7
					&& super.saveClickX <= 104 && super.saveClickY >= 464
					&& super.saveClickY <= 493) {
				Configuration.worldSwitch = !Configuration.worldSwitch;
			}
			int j = super.myHeight / 2 - 40;
			j += 30;
			j += 25;
			if (super.clickMode3 == 1 && super.saveClickY >= j - 15
					&& super.saveClickY < j)
				loginScreenCursorPos = 0;
			j += 15;
			if (super.clickMode3 == 1 && super.saveClickY >= j - 15
					&& super.saveClickY < j)
				loginScreenCursorPos = 1;
			j += 15;
			int i1 = super.myWidth / 2 - 80;
			int k1 = super.myHeight / 2 + 50;
			k1 += 20;
			if (super.clickMode3 == 1 && super.saveClickX >= i1 - 75
					&& super.saveClickX <= i1 + 75
					&& super.saveClickY >= k1 - 20
					&& super.saveClickY <= k1 + 20) {
				loginFailures = 0;
				login(myUsername, myPassword, false);
				if (loggedIn)
					return;
			}
			i1 = super.myWidth / 2 + 80;
			if (super.clickMode3 == 1 && super.saveClickX >= i1 - 75
					&& super.saveClickX <= i1 + 75
					&& super.saveClickY >= k1 - 20
					&& super.saveClickY <= k1 + 20) {
				loginScreenState = 0;
			}
			do {
				int l1 = readChar(-796);
				if (l1 == -1)
					break;
				boolean flag1 = false;
				for (int i2 = 0; i2 < validUserPassChars.length(); i2++) {
					if (l1 != validUserPassChars.charAt(i2))
						continue;
					flag1 = true;
					break;
				}

				if (loginScreenCursorPos == 0) {
					if (l1 == 8 && myUsername.length() > 0)
						myUsername = myUsername.substring(0,
								myUsername.length() - 1);
					if (l1 == 9 || l1 == 10 || l1 == 13)
						loginScreenCursorPos = 1;
					if (flag1)
						myUsername += (char) l1;
					if (myUsername.length() > 12)
						myUsername = myUsername.substring(0, 12);
				} else if (loginScreenCursorPos == 1) {
					if (l1 == 8 && myPassword.length() > 0)
						myPassword = myPassword.substring(0,
								myPassword.length() - 1);
					if (l1 == 9 || l1 == 10 || l1 == 13)
						loginScreenCursorPos = 0;
					if (flag1)
						myPassword += (char) l1;
					if (myPassword.length() > 20)
						myPassword = myPassword.substring(0, 20);
				}
			} while (true);
			return;
		} else if (loginScreenState == 3) {
			int k = super.myWidth / 2;
			int j1 = super.myHeight / 2 + 50;
			j1 += 20;
			if (super.clickMode3 == 1 && super.saveClickX >= k - 75
					&& super.saveClickX <= k + 75
					&& super.saveClickY >= j1 - 20
					&& super.saveClickY <= j1 + 20)
				loginScreenState = 0;
		}
	}

	private void method142(int i, int j, int k, int l, int i1, int j1, int k1) {
		if (i1 >= 1 && i >= 1 && i1 <= 102 && i <= 102) {
			if (lowMem && j != plane)
				return;
			int i2 = 0;
			if (j1 == 0)
				i2 = worldController.method300(j, i1, i);
			if (j1 == 1)
				i2 = worldController.method301(j, i1, i);
			if (j1 == 2)
				i2 = worldController.method302(j, i1, i);
			if (j1 == 3)
				i2 = worldController.method303(j, i1, i);
			if (i2 != 0) {
				int i3 = worldController.method304(j, i1, i, i2);
				int j2 = i2 >> 14 & 0x7fff;
				int k2 = i3 & 0x1f;
				int l2 = i3 >> 6;
				if (j1 == 0) {
					worldController.method291(i1, j, i, (byte) -119);
					ObjectDefinition class46 = ObjectDefinition.lookup(j2);
					if (class46.solid)
						aClass11Array1230[j].method215(l2, k2,
								class46.impenetrable, i1, i);
				}
				if (j1 == 1)
					worldController.method292(i, j, i1);
				if (j1 == 2) {
					worldController.method293(j, i1, i);
					ObjectDefinition class46_1 = ObjectDefinition.lookup(j2);
					if (i1 + class46_1.width > 103 || i + class46_1.width > 103
							|| i1 + class46_1.length > 103
							|| i + class46_1.length > 103)
						return;
					if (class46_1.solid)
						aClass11Array1230[j].method216(l2, class46_1.width, i1,
								i, class46_1.length, class46_1.impenetrable);
				}
				if (j1 == 3) {
					worldController.method294(j, i, i1);
					ObjectDefinition class46_2 = ObjectDefinition.lookup(j2);
					if (class46_2.solid && class46_2.isInteractive)
						aClass11Array1230[j].method218(i, i1);
				}
			}
			if (k1 >= 0) {
				int j3 = j;
				if (j3 < 3 && (byteGroundArray[1][i1][i] & 2) == 2)
					j3++;
				ObjectManager.method188(worldController, k, i, l, j3,
						aClass11Array1230[j], intGroundArray, i1, k1, j);
			}
		}
	}

	private void updatePlayers(int i, Buffer stream) {
		anInt839 = 0;
		anInt893 = 0;
		updatePlayerMovement(stream);
		method134(stream);
		updateOtherPlayerMovement(stream, i);
		refreshUpdateMasks(stream);
		for (int k = 0; k < anInt839; k++) {
			int l = anIntArray840[k];
			if (players[l].anInt1537 != loopCycle)
				players[l] = null;
		}

		if (stream.currentPosition != i) {
			Signlink.reporterror("Error packet size mismatch in getplayer pos:"
					+ stream.currentPosition + " psize:" + i);
			throw new RuntimeException("eek");
		}
		for (int i1 = 0; i1 < playerCount; i1++)
			if (players[playerIndices[i1]] == null) {
				Signlink.reporterror(myUsername
						+ " null entry in pl list - pos:" + i1 + " size:"
						+ playerCount);
				throw new RuntimeException("eek");
			}

	}

	private void setCameraPos(int j, int k, int l, int i1, int j1, int k1) {
		int l1 = 2048 - k & 0x7ff;
		int i2 = 2048 - j1 & 0x7ff;
		int j2 = 0;
		int k2 = 0;
		int l2 = j;
		if (l1 != 0) {
			int i3 = Model.SINE[l1];
			int k3 = Model.COSINE[l1];
			int i4 = k2 * k3 - l2 * i3 >> 16;
			l2 = k2 * i3 + l2 * k3 >> 16;
			k2 = i4;
		}
		if (i2 != 0) {
			int j3 = Model.SINE[i2];
			int l3 = Model.COSINE[i2];
			int j4 = l2 * j3 + j2 * l3 >> 16;
			l2 = l2 * l3 - j2 * j3 >> 16;
			j2 = j4;
		}
		xCameraPos = l - j2;
		zCameraPos = i1 - k2;
		yCameraPos = k1 - l2;
		yCameraCurve = k;
		xCameraCurve = j1;
	}

	/**
	 * This method updates default messages upon login to the
	 * desired text of the interface text.
	 */
	public void updateStrings(String message, int index) {
		switch (index) {
		case 1675:
			sendString(message, 17508);
			break;// Stab
		case 1676:
			sendString(message, 17509);
			break;// Slash
		case 1677:
			sendString(message, 17510);
			break;// Crush
		case 1678:
			sendString(message, 17511);
			break;// Magic
		case 1679:
			sendString(message, 17512);
			break;// Range
		case 1680:
			//sendString(message, 17513);
			break;// Stab
		case 1681:
			//sendString(message, 17514);
			break;// Slash
		case 1682:
			//sendString(message, 17515);
			break;// Crush
		case 1683:
			sendString(message, 17516);
			break;// Magic
		case 1684:
			sendString(message, 17517);
			break;// Range
		case 1686:
			sendString(message, 17518);
			break;// Strength
		case 1687:
			sendString(message, 17519);
			break;// Prayer
		}
	}

	/**
	 * Sends a string
	 */
	public void sendString(String text, int index) {
		Widget.interfaceCache[index].defaultText = text;
		if (Widget.interfaceCache[index].parent == tabInterfaceIDs[tabID]) {
		}
	}

	public void sendButtonClick(int button, int toggle, int type) {
		switch (type) {
		case 135:
			Widget class9 = Widget.interfaceCache[button];
			boolean flag8 = true;
			if (class9.contentType > 0)
				flag8 = promptUserForInput(class9);
			if (flag8) {
				outgoing.createFrame(185);
				outgoing.writeShort(button);
			}
			break;
		case 646:
			outgoing.createFrame(185);
			outgoing.writeShort(button);
			Widget widget = Widget.interfaceCache[button];
			if (widget.scripts != null && widget.scripts[0][0] == 5) {
				if (variousSettings[toggle] != widget.scriptDefaults[0]) {
					variousSettings[toggle] = widget.scriptDefaults[0];
					adjustVolume(toggle);
				}
			}
			break;
		case 169:
			outgoing.createFrame(185);
			outgoing.writeShort(button);
			Widget class9_3 = Widget.interfaceCache[button];
			if (class9_3.scripts != null && class9_3.scripts[0][0] == 5) {
				variousSettings[toggle] = 1 - variousSettings[toggle];
				adjustVolume(toggle);
			}
			switch (button) {
			case 74214:
				System.out.println("toggle = " + toggle);
				if (toggle == 0)
					sendConfiguration(173, toggle);
				if (toggle == 1)
					sendButtonClick(153, 173, 646);
				break;
			}
			break;
		}
	}

	/**
	 * Sets button configurations on interfaces.
	 */
	public void sendConfiguration(int id, int state) {
		anIntArray1045[id] = state;
		if (variousSettings[id] != state) {
			variousSettings[id] = state;
			adjustVolume(id);
			if (dialogueId != -1)
				inputTaken = true;
		}
	}

	/**
	 * Clears the screen of all open interfaces.
	 */
	public void clearScreen() {
		if (overlayInterfaceId != -1) {
			overlayInterfaceId = -1;
			tabAreaAltered = true;
		}
		if (backDialogueId != -1) {
			backDialogueId = -1;
			inputTaken = true;
		}
		if (inputDialogState != 0) {
			inputDialogState = 0;
			inputTaken = true;
		}
		openInterfaceId = -1;
		continuedDialogue = false;
	}

	/**
	 * Displays an interface over the sidebar area.
	 */
	public void inventoryOverlay(int interfaceId, int sideInterfaceId) {
		if (backDialogueId != -1) {
			backDialogueId = -1;
			inputTaken = true;
		}
		if (inputDialogState != 0) {
			inputDialogState = 0;
			inputTaken = true;
		}
		openInterfaceId = interfaceId;
		overlayInterfaceId = sideInterfaceId;
		tabAreaAltered = true;
		continuedDialogue = false;
	}

	private boolean parsePacket() {
		if (socketStream == null)
			return false;
		try {
			int i = socketStream.available();
			if (i == 0)
				return false;
			if (opCode == -1) {
				socketStream.flushInputStream(incoming.payload, 1);
				opCode = incoming.payload[0] & 0xff;
				if (encryption != null)
					opCode = opCode - encryption.getNextKey() & 0xff;
				packetSize = PacketConstants.packetSizes[opCode];
				i--;
			}
			if (packetSize == -1)
				if (i > 0) {
					socketStream.flushInputStream(incoming.payload, 1);
					packetSize = incoming.payload[0] & 0xff;
					i--;
				} else {
					return false;
				}
			if (packetSize == -2)
				if (i > 1) {
					socketStream.flushInputStream(incoming.payload, 2);
					incoming.currentPosition = 0;
					packetSize = incoming.readUShort();
					i -= 2;
				} else {
					return false;
				}
			if (i < packetSize)
				return false;
			incoming.currentPosition = 0;
			socketStream.flushInputStream(incoming.payload, packetSize);
			timeoutCounter = 0;
			thirdLastOpcode = secondLastOpcode;
			secondLastOpcode = lastOpcode;
			lastOpcode = opCode;
			switch (opCode) {
			case PacketConstants.PLAYER_UPDATING:
				updatePlayers(packetSize, incoming);
				aBoolean1080 = false;
				opCode = -1;
				return true;

			case 124:
				int skillID = incoming.readUShort();
				int gainedXP = incoming.readUShort();
				addToXPCounter(skillID, gainedXP);
				opCode = -1;
				return true;

			case PacketConstants.OPEN_WELCOME_SCREEN:
				daysSinceRecovChange = incoming.readNegUByte();
				unreadMessages = incoming.readUShortA();
				membersInt = incoming.readUnsignedByte();
				anInt1193 = incoming.readIMEInt();
				daysSinceLastLogin = incoming.readUShort();
				if (anInt1193 != 0 && openInterfaceId == -1) {
					Signlink.dnslookup(TextClass.method586(anInt1193));
					clearTopInterfaces();
					char c = '\u028A';
					if (daysSinceRecovChange != 201 || membersInt == 1)
						c = '\u028F';
					reportAbuseInput = "";
					canMute = false;
					for (int k9 = 0; k9 < Widget.interfaceCache.length; k9++) {
						if (Widget.interfaceCache[k9] == null
								|| Widget.interfaceCache[k9].contentType != c)
							continue;
						openInterfaceId = Widget.interfaceCache[k9].parent;

					}
				}
				opCode = -1;
				return true;

			case PacketConstants.DELETE_GROUND_ITEM:
				anInt1268 = incoming.readNegUByte();
				anInt1269 = incoming.readUByteS();
				for (int j = anInt1268; j < anInt1268 + 8; j++) {
					for (int l9 = anInt1269; l9 < anInt1269 + 8; l9++)
						if (groundItems[plane][j][l9] != null) {
							groundItems[plane][j][l9] = null;
							spawnGroundItem(j, l9);
						}
				}
				for (TemporaryObject class30_sub1 = (TemporaryObject) spawns
						.reverseGetFirst(); class30_sub1 != null; class30_sub1 = (TemporaryObject) spawns
						.reverseGetNext())
					if (class30_sub1.anInt1297 >= anInt1268
							&& class30_sub1.anInt1297 < anInt1268 + 8
							&& class30_sub1.anInt1298 >= anInt1269
							&& class30_sub1.anInt1298 < anInt1269 + 8
							&& class30_sub1.anInt1295 == plane)
						class30_sub1.anInt1294 = 0;
				opCode = -1;
				return true;

			case PacketConstants.SHOW_PLAYER_HEAD_ON_INTERFACE:
				int k = incoming.readLEUShortA();
				Widget.interfaceCache[k].defaultMediaType = 3;
				if (localPlayer.desc == null)
					Widget.interfaceCache[k].defaultMedia = (localPlayer.anIntArray1700[0] << 25)
							+ (localPlayer.anIntArray1700[4] << 20)
							+ (localPlayer.equipment[0] << 15)
							+ (localPlayer.equipment[8] << 10)
							+ (localPlayer.equipment[11] << 5)
							+ localPlayer.equipment[1];
				else
					Widget.interfaceCache[k].defaultMedia = (int) (0x12345678L + localPlayer.desc.interfaceType);
				opCode = -1;
				return true;

			case PacketConstants.CLAN_CHAT:
				try {
					name = incoming.readString();
					defaultText = incoming.readString();
					clanname = incoming.readString();
					rights = incoming.readUShort();
					// defaultText = TextInput.processText(defaultText);
					// defaultText = Censor.doCensor(defaultText);
					System.out.println(clanname);
					pushMessage(defaultText, 16, name);
				} catch (Exception e) {
					e.printStackTrace();
				}
				opCode = -1;
				return true;

			case PacketConstants.RESET_CAMERA:
				aBoolean1160 = false;
				for (int l = 0; l < 5; l++)
					aBooleanArray876[l] = false;
				xpCounter = 0;
				opCode = -1;
				return true;

			case PacketConstants.CLEAN_ITEMS_OF_INTERFACE:
				int i1 = incoming.readLEUShort();
				Widget class9 = Widget.interfaceCache[i1];
				for (int k15 = 0; k15 < class9.inventoryItemId.length; k15++) {
					class9.inventoryItemId[k15] = -1;
					class9.inventoryItemId[k15] = 0;
				}
				opCode = -1;
				return true;

			case PacketConstants.SHOW_IGNORE_NAMES:
				ignoreCount = packetSize / 8;
				for (int j1 = 0; j1 < ignoreCount; j1++)
					ignoreListAsLongs[j1] = incoming.readLong();
				opCode = -1;
				return true;

			case PacketConstants.SPIN_CAMERA:
				aBoolean1160 = true;
				x = incoming.readUnsignedByte();
				y = incoming.readUnsignedByte();
				height = incoming.readUShort();
				speed = incoming.readUnsignedByte();
				angle = incoming.readUnsignedByte();
				if (angle >= 100) {
					xCameraPos = x * 128 + 64;
					yCameraPos = y * 128 + 64;
					zCameraPos = method42(plane, yCameraPos, xCameraPos)
							- height;
				}
				opCode = -1;
				return true;

			case PacketConstants.SEND_SKILL:
				int k1 = incoming.readUnsignedByte();
				int i10 = incoming.readMEInt();
				int l15 = incoming.readUnsignedByte();
				if (k1 < currentExp.length) {
					int xp = i10 - currentExp[k1];
					if (currentExp[k1] > -1)
						addToXPCounter(k1, xp);
					currentExp[k1] = i10;
					currentStats[k1] = l15;
					maxStats[k1] = 1;
					for (int k20 = 0; k20 < 98; k20++)
						if (i10 >= anIntArray1019[k20])
							maxStats[k1] = k20 + 2;
				}
				opCode = -1;
				return true;

			case PacketConstants.SEND_SIDE_TAB:
				int l1 = incoming.readUShort();
				int j10 = incoming.readUByteA();
				if (l1 == 65535)
					l1 = -1;
				tabInterfaceIDs[j10] = l1;
				tabAreaAltered = true;
				opCode = -1;
				return true;

			case PacketConstants.PLAY_SONG:
				int id = incoming.readLEUShort();
				if (id == 65535)
					id = -1;
				if (id != currentSong && Configuration.enableMusic && !lowMem
						&& prevSong == 0) {
					nextSong = id;
					songChanging = true;
					onDemandFetcher.provide(2, nextSong);
				}
				currentSong = id;
				opCode = -1;
				return true;

			case PacketConstants.NEXT_OR_PREVIOUS_SONG:
				int next = incoming.readLEUShortA();
				int previous = incoming.readUShortA();
				if (Configuration.enableMusic && !lowMem) {
					nextSong = next;
					songChanging = false;
					onDemandFetcher.provide(2, nextSong);
					prevSong = previous;
				}
				opCode = -1;
				return true;

			case PacketConstants.LOGOUT:
				resetLogout();
				opCode = -1;
				return false;

			case PacketConstants.MOVE_COMPONENT:
				int x = incoming.readShort();
				int y = incoming.readLEShort();
				int compId = incoming.readLEUShort();
				Widget childInterface = Widget.interfaceCache[compId];
				childInterface.x = x;
				childInterface.anInt265 = y;
				opCode = -1;
				return true;

			case PacketConstants.SEND_MAP_REGION:
			case PacketConstants.SEND_REGION_MAP_REGION:
				int regionX = anInt1069;
				int regionY = anInt1070;
				if (opCode == 73) {
					regionX = incoming.readUShortA();
					regionY = incoming.readUShort();
					aBoolean1159 = false;
				}
				if (opCode == 241) {
					regionY = incoming.readUShortA();
					incoming.initBitAccess();
					for (int j16 = 0; j16 < 4; j16++) {
						for (int l20 = 0; l20 < 13; l20++) {
							for (int j23 = 0; j23 < 13; j23++) {
								int i26 = incoming.readBits(1);
								if (i26 == 1)
									anIntArrayArrayArray1129[j16][l20][j23] = incoming
											.readBits(26);
								else
									anIntArrayArrayArray1129[j16][l20][j23] = -1;
							}
						}
					}
					incoming.finishBitAccess();
					regionX = incoming.readUShort();
					aBoolean1159 = true;
				}
				if (anInt1069 == regionX && anInt1070 == regionY
						&& loadingStage == 2) {
					opCode = -1;
					return true;
				}
				anInt1069 = regionX;
				anInt1070 = regionY;
				baseX = (anInt1069 - 6) * 8;
				baseY = (anInt1070 - 6) * 8;
				aBoolean1141 = (anInt1069 / 8 == 48 || anInt1069 / 8 == 49)
						&& anInt1070 / 8 == 48;
				if (anInt1069 / 8 == 48 && anInt1070 / 8 == 148)
					aBoolean1141 = true;
				loadingStage = 1;
				aLong824 = System.currentTimeMillis();
				gameScreenImageProducer.initDrawingArea();
				drawLoadingMessages(1, "Loading - please wait.", null);
				gameScreenImageProducer.drawGraphics(
						frameMode == ScreenMode.FIXED ? 4 : 0, super.graphics,
						frameMode == ScreenMode.FIXED ? 4 : 0);
				if (opCode == 73) {
					int k16 = 0;
					for (int i21 = (anInt1069 - 6) / 8; i21 <= (anInt1069 + 6) / 8; i21++) {
						for (int k23 = (anInt1070 - 6) / 8; k23 <= (anInt1070 + 6) / 8; k23++)
							k16++;
					}
					aByteArrayArray1183 = new byte[k16][];
					aByteArrayArray1247 = new byte[k16][];
					anIntArray1234 = new int[k16];
					anIntArray1235 = new int[k16];
					anIntArray1236 = new int[k16];
					k16 = 0;
					for (int l23 = (anInt1069 - 6) / 8; l23 <= (anInt1069 + 6) / 8; l23++) {
						for (int j26 = (anInt1070 - 6) / 8; j26 <= (anInt1070 + 6) / 8; j26++) {
							anIntArray1234[k16] = (l23 << 8) + j26;
							if (aBoolean1141
									&& (j26 == 49 || j26 == 149 || j26 == 147
											|| l23 == 50 || l23 == 49
											&& j26 == 47)) {
								anIntArray1235[k16] = -1;
								anIntArray1236[k16] = -1;
								k16++;
							} else {
								int k28 = anIntArray1235[k16] = onDemandFetcher
										.method562(0, j26, l23);
								if (k28 != -1)
									onDemandFetcher.provide(3, k28);
								int j30 = anIntArray1236[k16] = onDemandFetcher
										.method562(1, j26, l23);
								if (j30 != -1)
									onDemandFetcher.provide(3, j30);
								k16++;
							}
						}
					}
				}
				if (opCode == 241) {
					int l16 = 0;
					int ai[] = new int[676];
					for (int i24 = 0; i24 < 4; i24++) {
						for (int k26 = 0; k26 < 13; k26++) {
							for (int l28 = 0; l28 < 13; l28++) {
								int k30 = anIntArrayArrayArray1129[i24][k26][l28];
								if (k30 != -1) {
									int k31 = k30 >> 14 & 0x3ff;
									int i32 = k30 >> 3 & 0x7ff;
									int k32 = (k31 / 8 << 8) + i32 / 8;
									for (int j33 = 0; j33 < l16; j33++) {
										if (ai[j33] != k32)
											continue;
										k32 = -1;

									}
									if (k32 != -1)
										ai[l16++] = k32;
								}
							}
						}
					}
					aByteArrayArray1183 = new byte[l16][];
					aByteArrayArray1247 = new byte[l16][];
					anIntArray1234 = new int[l16];
					anIntArray1235 = new int[l16];
					anIntArray1236 = new int[l16];
					for (int l26 = 0; l26 < l16; l26++) {
						int i29 = anIntArray1234[l26] = ai[l26];
						int l30 = i29 >> 8 & 0xff;
						int l31 = i29 & 0xff;
						int j32 = anIntArray1235[l26] = onDemandFetcher
								.method562(0, l31, l30);
						if (j32 != -1)
							onDemandFetcher.provide(3, j32);
						int i33 = anIntArray1236[l26] = onDemandFetcher
								.method562(1, l31, l30);
						if (i33 != -1)
							onDemandFetcher.provide(3, i33);
					}
				}
				int i17 = baseX - anInt1036;
				int j21 = baseY - anInt1037;
				anInt1036 = baseX;
				anInt1037 = baseY;
				for (int j24 = 0; j24 < 16384; j24++) {
					Npc npc = npcs[j24];
					if (npc != null) {
						for (int j29 = 0; j29 < 10; j29++) {
							npc.pathX[j29] -= i17;
							npc.pathY[j29] -= j21;
						}
						npc.x -= i17 * 128;
						npc.y -= j21 * 128;
					}
				}
				for (int i27 = 0; i27 < maxPlayers; i27++) {
					Player player = players[i27];
					if (player != null) {
						for (int i31 = 0; i31 < 10; i31++) {
							player.pathX[i31] -= i17;
							player.pathY[i31] -= j21;
						}
						player.x -= i17 * 128;
						player.y -= j21 * 128;
					}
				}
				aBoolean1080 = true;
				byte byte1 = 0;
				byte byte2 = 104;
				byte byte3 = 1;
				if (i17 < 0) {
					byte1 = 103;
					byte2 = -1;
					byte3 = -1;
				}
				byte byte4 = 0;
				byte byte5 = 104;
				byte byte6 = 1;
				if (j21 < 0) {
					byte4 = 103;
					byte5 = -1;
					byte6 = -1;
				}
				for (int k33 = byte1; k33 != byte2; k33 += byte3) {
					for (int l33 = byte4; l33 != byte5; l33 += byte6) {
						int i34 = k33 + i17;
						int j34 = l33 + j21;
						for (int k34 = 0; k34 < 4; k34++)
							if (i34 >= 0 && j34 >= 0 && i34 < 104 && j34 < 104)
								groundItems[k34][k33][l33] = groundItems[k34][i34][j34];
							else
								groundItems[k34][k33][l33] = null;
					}
				}
				for (TemporaryObject class30_sub1_1 = (TemporaryObject) spawns
						.reverseGetFirst(); class30_sub1_1 != null; class30_sub1_1 = (TemporaryObject) spawns
						.reverseGetNext()) {
					class30_sub1_1.anInt1297 -= i17;
					class30_sub1_1.anInt1298 -= j21;
					if (class30_sub1_1.anInt1297 < 0
							|| class30_sub1_1.anInt1298 < 0
							|| class30_sub1_1.anInt1297 >= 104
							|| class30_sub1_1.anInt1298 >= 104)
						class30_sub1_1.unlink();
				}
				if (destinationX != 0) {
					destinationX -= i17;
					destY -= j21;
				}
				aBoolean1160 = false;
				opCode = -1;
				return true;

			case 208:
				int i3 = incoming.readLEShort();
				if (i3 >= 0)
					writeInterface(i3);
				openWalkableInterface = i3;
				opCode = -1;
				return true;

			case 99:
				minimapState = incoming.readUnsignedByte();
				opCode = -1;
				return true;

			case 75:
				int j3 = incoming.readLEUShortA();
				int j11 = incoming.readLEUShortA();
				Widget.interfaceCache[j11].defaultMediaType = 2;
				Widget.interfaceCache[j11].defaultMedia = j3;
				opCode = -1;
				return true;

			case PacketConstants.SYSTEM_UPDATE:
				systemUpdateTime = incoming.readLEUShort() * 30;
				opCode = -1;
				return true;

			case 60:
				anInt1269 = incoming.readUnsignedByte();
				anInt1268 = incoming.readNegUByte();
				while (incoming.currentPosition < packetSize) {
					int k3 = incoming.readUnsignedByte();
					method137(incoming, k3);
				}
				opCode = -1;
				return true;

			case 35:
				int l3 = incoming.readUnsignedByte();
				int k11 = incoming.readUnsignedByte();
				int j17 = incoming.readUnsignedByte();
				int k21 = incoming.readUnsignedByte();
				aBooleanArray876[l3] = true;
				anIntArray873[l3] = k11;
				anIntArray1203[l3] = j17;
				anIntArray928[l3] = k21;
				anIntArray1030[l3] = 0;
				opCode = -1;
				return true;

			case PacketConstants.PLAY_SOUND_EFFECT:
				int soundId = incoming.readUShort();
				int type = incoming.readUnsignedByte();
				int delay = incoming.readUShort();
				int volume = incoming.readUShort();
				tracks[trackCount] = soundId;
				trackLoops[trackCount] = type;
				soundDelay[trackCount] = delay + SoundTrack.delays[soundId];
				soundVolume[trackCount] = volume;
				trackCount++;
				opCode = -1;
				return true;

			case 104:
				int j4 = incoming.readNegUByte();
				int i12 = incoming.readUByteA();
				String s6 = incoming.readString();
				if (j4 >= 1 && j4 <= 5) {
					if (s6.equalsIgnoreCase("null"))
						s6 = null;
					atPlayerActions[j4 - 1] = s6;
					atPlayerArray[j4 - 1] = i12 == 0;
				}
				opCode = -1;
				return true;

			case 78:
				destinationX = 0;
				opCode = -1;
				return true;

			case 253:
				String s = incoming.readString();
				if (s.endsWith(":tradereq:")) {
					String s3 = s.substring(0, s.indexOf(":"));
					long l17 = TextClass.longForName(s3);
					boolean flag2 = false;
					for (int j27 = 0; j27 < ignoreCount; j27++) {
						if (ignoreListAsLongs[j27] != l17)
							continue;
						flag2 = true;

					}
					if (!flag2 && anInt1251 == 0)
						pushMessage("wishes to trade with you.", 4, s3);
				} else if (s.endsWith(":clan:")) {
					String s4 = s.substring(0, s.indexOf(":"));
					TextClass.longForName(s4);
					pushMessage("Clan: ", 8, s4);
				} else if (s.endsWith("#url#")) {
					String link = s.substring(0, s.indexOf("#"));
					pushMessage("Join us at: ", 9, link);
				} else if (s.endsWith(":duelreq:")) {
					String s4 = s.substring(0, s.indexOf(":"));
					long l18 = TextClass.longForName(s4);
					boolean flag3 = false;
					for (int k27 = 0; k27 < ignoreCount; k27++) {
						if (ignoreListAsLongs[k27] != l18)
							continue;
						flag3 = true;

					}
					if (!flag3 && anInt1251 == 0)
						pushMessage("wishes to duel with you.", 8, s4);
				} else if (s.endsWith(":chalreq:")) {
					String s5 = s.substring(0, s.indexOf(":"));
					long l19 = TextClass.longForName(s5);
					boolean flag4 = false;
					for (int l27 = 0; l27 < ignoreCount; l27++) {
						if (ignoreListAsLongs[l27] != l19)
							continue;
						flag4 = true;

					}
					if (!flag4 && anInt1251 == 0) {
						String s8 = s.substring(s.indexOf(":") + 1,
								s.length() - 9);
						pushMessage(s8, 8, s5);
					}
				} else if (s.endsWith(":resetautocast:")) {
					autocast = false;
					autoCastId = 0;
					cacheSprite[43].drawSprite(-100, -100);
				} else {
					pushMessage(s, 0, "");
				}
				opCode = -1;
				return true;

			case 1:
				for (int k4 = 0; k4 < players.length; k4++)
					if (players[k4] != null)
						players[k4].emoteAnimation = -1;
				for (int j12 = 0; j12 < npcs.length; j12++)
					if (npcs[j12] != null)
						npcs[j12].emoteAnimation = -1;
				opCode = -1;
				return true;

			case 50:
				long l4 = incoming.readLong();
				int i18 = incoming.readUnsignedByte();
				String s7 = TextClass.fixName(TextClass.nameForLong(l4));
				for (int k24 = 0; k24 < friendsCount; k24++) {
					if (l4 != friendsListAsLongs[k24])
						continue;
					if (friendsNodeIDs[k24] != i18) {
						friendsNodeIDs[k24] = i18;
						if (i18 >= 2) {
							pushMessage(s7 + " has logged in.", 5, "");
						}
						if (i18 <= 1) {
							pushMessage(s7 + " has logged out.", 5, "");
						}
					}
					s7 = null;

				}
				if (s7 != null && friendsCount < 200) {
					friendsListAsLongs[friendsCount] = l4;
					friendsList[friendsCount] = s7;
					friendsNodeIDs[friendsCount] = i18;
					friendsCount++;
				}
				for (boolean flag6 = false; !flag6;) {
					flag6 = true;
					for (int k29 = 0; k29 < friendsCount - 1; k29++)
						if (friendsNodeIDs[k29] != nodeID
								&& friendsNodeIDs[k29 + 1] == nodeID
								|| friendsNodeIDs[k29] == 0
								&& friendsNodeIDs[k29 + 1] != 0) {
							int j31 = friendsNodeIDs[k29];
							friendsNodeIDs[k29] = friendsNodeIDs[k29 + 1];
							friendsNodeIDs[k29 + 1] = j31;
							String s10 = friendsList[k29];
							friendsList[k29] = friendsList[k29 + 1];
							friendsList[k29 + 1] = s10;
							long l32 = friendsListAsLongs[k29];
							friendsListAsLongs[k29] = friendsListAsLongs[k29 + 1];
							friendsListAsLongs[k29 + 1] = l32;
							flag6 = false;
						}
				}
				opCode = -1;
				return true;

			case 110:
				if (tabID == 12) {
				}
				energy = incoming.readUnsignedByte();
				opCode = -1;
				return true;

			case 254:
				hintIconDrawType = incoming.readUnsignedByte();
				if (hintIconDrawType == 1)
					hintIconNpcId = incoming.readUShort();
				if (hintIconDrawType >= 2 && hintIconDrawType <= 6) {
					if (hintIconDrawType == 2) {
						anInt937 = 64;
						anInt938 = 64;
					}
					if (hintIconDrawType == 3) {
						anInt937 = 0;
						anInt938 = 64;
					}
					if (hintIconDrawType == 4) {
						anInt937 = 128;
						anInt938 = 64;
					}
					if (hintIconDrawType == 5) {
						anInt937 = 64;
						anInt938 = 0;
					}
					if (hintIconDrawType == 6) {
						anInt937 = 64;
						anInt938 = 128;
					}
					hintIconDrawType = 2;
					hintIconX = incoming.readUShort();
					hintIconY = incoming.readUShort();
					anInt936 = incoming.readUnsignedByte();
				}
				if (hintIconDrawType == 10)
					hintIconPlayerId = incoming.readUShort();
				opCode = -1;
				return true;

			case 248:
				int i5 = incoming.readUShortA();
				int k12 = incoming.readUShort();
				if (backDialogueId != -1) {
					backDialogueId = -1;
					inputTaken = true;
				}
				if (inputDialogState != 0) {
					inputDialogState = 0;
					inputTaken = true;
				}
				openInterfaceId = i5;
				overlayInterfaceId = k12;
				tabAreaAltered = true;
				continuedDialogue = false;
				opCode = -1;
				return true;

			case 79:
				int j5 = incoming.readLEUShort();
				int l12 = incoming.readUShortA();
				Widget class9_3 = Widget.interfaceCache[j5];
				if (class9_3 != null && class9_3.type == 0) {
					if (l12 < 0)
						l12 = 0;
					if (l12 > class9_3.scrollMax - class9_3.height)
						l12 = class9_3.scrollMax - class9_3.height;
					class9_3.scrollPosition = l12;
				}
				opCode = -1;
				return true;

			case 68:
				for (int k5 = 0; k5 < variousSettings.length; k5++)
					if (variousSettings[k5] != anIntArray1045[k5]) {
						variousSettings[k5] = anIntArray1045[k5];
						adjustVolume(k5);
					}
				opCode = -1;
				return true;

			case 196:
				long l5 = incoming.readLong();
				int j18 = incoming.readInt();
				int l21 = incoming.readUnsignedByte();
				boolean flag5 = false;
				for (int i28 = 0; i28 < 100; i28++) {
					if (anIntArray1240[i28] != j18)
						continue;
					flag5 = true;

				}
				if (l21 <= 1) {
					for (int l29 = 0; l29 < ignoreCount; l29++) {
						if (ignoreListAsLongs[l29] != l5)
							continue;
						flag5 = true;

					}
				}
				if (!flag5 && anInt1251 == 0)
					try {
						anIntArray1240[anInt1169] = j18;
						anInt1169 = (anInt1169 + 1) % 100;
						String s9 = TextInput.method525(packetSize - 13,
								incoming);
						// if(l21 != 3)
						// s9 = Censor.doCensor(s9);
						if (l21 == 2 || l21 == 3)
							pushMessage(
									s9,
									7,
									"@cr2@"
											+ TextClass.fixName(TextClass
													.nameForLong(l5)));
						else if (l21 == 1)
							pushMessage(
									s9,
									7,
									"@cr1@"
											+ TextClass.fixName(TextClass
													.nameForLong(l5)));
						else
							pushMessage(s9, 3, TextClass.fixName(TextClass
									.nameForLong(l5)));
					} catch (Exception exception1) {
						Signlink.reporterror("cde1");
					}
				opCode = -1;
				return true;

			case 85:
				anInt1269 = incoming.readNegUByte();
				anInt1268 = incoming.readNegUByte();
				opCode = -1;
				return true;

			case 24:
				flashingSidebarId = incoming.readUByteS();
				if (flashingSidebarId == tabID) {
					if (flashingSidebarId == 3)
						tabID = 1;
					else
						tabID = 3;
				}
				opCode = -1;
				return true;

			case 246:
				int i6 = incoming.readLEUShort();
				int i13 = incoming.readUShort();
				int k18 = incoming.readUShort();
				if (k18 == 65535) {
					Widget.interfaceCache[i6].defaultMediaType = 0;
					opCode = -1;
					return true;
				} else {
					ItemDefinition itemDef = ItemDefinition.lookup(k18);
					Widget.interfaceCache[i6].defaultMediaType = 4;
					Widget.interfaceCache[i6].defaultMedia = k18;
					Widget.interfaceCache[i6].modelRotation1 = itemDef.rotation_y;
					Widget.interfaceCache[i6].modelRotation2 = itemDef.rotation_y;
					Widget.interfaceCache[i6].modelZoom = (itemDef.model_zoom * 100)
							/ i13;
					opCode = -1;
					return true;
				}

			case 171:
				boolean flag1 = incoming.readUnsignedByte() == 1;
				int j13 = incoming.readUShort();
				Widget.interfaceCache[j13].hoverOnly = flag1;
				opCode = -1;
				return true;

			case 142:
				int j6 = incoming.readLEUShort();
				writeInterface(j6);
				if (backDialogueId != -1) {
					backDialogueId = -1;
					inputTaken = true;
				}
				if (inputDialogState != 0) {
					inputDialogState = 0;
					inputTaken = true;
				}
				overlayInterfaceId = j6;
				tabAreaAltered = true;
				openInterfaceId = -1;
				continuedDialogue = false;
				opCode = -1;
				return true;

			case 126:
				try {
					String text = incoming.readString();
					int frame = incoming.readUShortA();
					if (text.startsWith("www.")) {
						launchURL(text);
					}
					if (text.startsWith(":quicks:"))
						clickedQuickPrayers = text.substring(8)
								.equalsIgnoreCase("on") ? true : false;
					if (text.startsWith(":prayer:"))
						prayerBook = text.substring(8);
					updateStrings(text, frame);
					sendString(text, frame);
					if (frame >= 18144 && frame <= 18244) {
						clanList[frame - 18144] = text;
					}
				} catch (Exception e) {
				}
				opCode = -1;
				return true;

			case 206:
				publicChatMode = incoming.readUnsignedByte();
				privateChatMode = incoming.readUnsignedByte();
				tradeMode = incoming.readUnsignedByte();
				inputTaken = true;
				opCode = -1;
				return true;

			case 240:
				if (tabID == 12) {
				}
				weight = incoming.readShort();
				opCode = -1;
				return true;

			case 8:
				int k6 = incoming.readLEUShortA();
				int l13 = incoming.readUShort();
				Widget.interfaceCache[k6].defaultMediaType = 1;
				Widget.interfaceCache[k6].defaultMedia = l13;
				opCode = -1;
				return true;

			case 122:
				int l6 = incoming.readLEUShortA();
				int i14 = incoming.readLEUShortA();
				int i19 = i14 >> 10 & 0x1f;
				int i22 = i14 >> 5 & 0x1f;
				int l24 = i14 & 0x1f;
				Widget.interfaceCache[l6].textColor = (i19 << 19) + (i22 << 11)
						+ (l24 << 3);
				opCode = -1;
				return true;

			case 53:
				int i7 = incoming.readUShort();
				Widget class9_1 = Widget.interfaceCache[i7];
				int j19 = incoming.readUShort();
				for (int j22 = 0; j22 < j19; j22++) {
					int i25 = incoming.readUnsignedByte();
					if (i25 == 255)
						i25 = incoming.readIMEInt();
					class9_1.inventoryItemId[j22] = incoming.readLEUShortA();
					class9_1.invStackSizes[j22] = i25;
				}
				for (int j25 = j19; j25 < class9_1.inventoryItemId.length; j25++) {
					class9_1.inventoryItemId[j25] = 0;
					class9_1.invStackSizes[j25] = 0;
				}
				opCode = -1;
				return true;

			case 230:
				int j7 = incoming.readUShortA();
				int j14 = incoming.readUShort();
				int k19 = incoming.readUShort();
				int k22 = incoming.readLEUShortA();
				Widget.interfaceCache[j14].modelRotation1 = k19;
				Widget.interfaceCache[j14].modelRotation2 = k22;
				Widget.interfaceCache[j14].modelZoom = j7;
				opCode = -1;
				return true;

			case 221:
				friendServerStatus = incoming.readUnsignedByte();
				opCode = -1;
				return true;

			case 177:
				aBoolean1160 = true;
				anInt995 = incoming.readUnsignedByte();
				anInt996 = incoming.readUnsignedByte();
				anInt997 = incoming.readUShort();
				anInt998 = incoming.readUnsignedByte();
				anInt999 = incoming.readUnsignedByte();
				if (anInt999 >= 100) {
					int k7 = anInt995 * 128 + 64;
					int k14 = anInt996 * 128 + 64;
					int i20 = method42(plane, k14, k7) - anInt997;
					int l22 = k7 - xCameraPos;
					int k25 = i20 - zCameraPos;
					int j28 = k14 - yCameraPos;
					int i30 = (int) Math.sqrt(l22 * l22 + j28 * j28);
					yCameraCurve = (int) (Math.atan2(k25, i30) * 325.94900000000001D) & 0x7ff;
					xCameraCurve = (int) (Math.atan2(l22, j28) * -325.94900000000001D) & 0x7ff;
					if (yCameraCurve < 128)
						yCameraCurve = 128;
					if (yCameraCurve > 383)
						yCameraCurve = 383;
				}
				opCode = -1;
				return true;

			case 249:
				anInt1046 = incoming.readUByteA();
				unknownInt10 = incoming.readLEUShortA();
				opCode = -1;
				return true;

			case 65:
				updateNPCs(incoming, packetSize);
				opCode = -1;
				return true;

			case 27:
				messagePromptRaised = false;
				inputDialogState = 1;
				amountOrNameInput = "";
				inputTaken = true;
				opCode = -1;
				return true;

			case 187:
				messagePromptRaised = false;
				inputDialogState = 2;
				amountOrNameInput = "";
				inputTaken = true;
				opCode = -1;
				return true;

			case 97:
				int interfaceId = incoming.readUShort();
				writeInterface(interfaceId);
				if (overlayInterfaceId != -1) {
					overlayInterfaceId = -1;
					tabAreaAltered = true;
				}
				if (backDialogueId != -1) {
					backDialogueId = -1;
					inputTaken = true;
				}
				if (inputDialogState != 0) {
					inputDialogState = 0;
					inputTaken = true;
				}
				if (interfaceId == 15244) {
					fullscreenInterfaceID = 17511;
					openInterfaceId = 15244;
				}
				openInterfaceId = interfaceId;
				continuedDialogue = false;
				opCode = -1;

				return true;

			case 218:
				int i8 = incoming.readLEShortA();
				dialogueId = i8;
				inputTaken = true;
				opCode = -1;
				return true;

			case 87:
				int j8 = incoming.readLEUShort();
				int l14 = incoming.readMEInt();
				anIntArray1045[j8] = l14;
				if (variousSettings[j8] != l14) {
					variousSettings[j8] = l14;
					adjustVolume(j8);
					if (dialogueId != -1)
						inputTaken = true;
				}
				opCode = -1;
				return true;

			case 36:
				int k8 = incoming.readLEUShort();
				byte byte0 = incoming.readSignedByte();
				anIntArray1045[k8] = byte0;
				if (variousSettings[k8] != byte0) {
					variousSettings[k8] = byte0;
					adjustVolume(k8);
					if (dialogueId != -1)
						inputTaken = true;
				}
				opCode = -1;
				return true;

			case 61:
				multicombat = incoming.readUnsignedByte();
				opCode = -1;
				return true;

			case 200:
				int l8 = incoming.readUShort();
				int i15 = incoming.readShort();
				Widget class9_4 = Widget.interfaceCache[l8];
				class9_4.anInt257 = i15;
				opCode = -1;
				return true;

			case 219:
				if (overlayInterfaceId != -1) {
					overlayInterfaceId = -1;
					tabAreaAltered = true;
				}
				if (backDialogueId != -1) {
					backDialogueId = -1;
					inputTaken = true;
				}
				if (inputDialogState != 0) {
					inputDialogState = 0;
					inputTaken = true;
				}
				openInterfaceId = -1;
				continuedDialogue = false;
				opCode = -1;
				return true;

			case 34:
				int i9 = incoming.readUShort();
				Widget childInterface2 = Widget.interfaceCache[i9];
				while (incoming.currentPosition < packetSize) {
					int j20 = incoming.readUSmart();
					int i23 = incoming.readUShort();
					int l25 = incoming.readUnsignedByte();
					if (l25 == 255)
						l25 = incoming.readInt();
					if (j20 >= 0
							&& j20 < childInterface2.inventoryItemId.length) {
						childInterface2.inventoryItemId[j20] = i23;
						childInterface2.invStackSizes[j20] = l25;
					}
				}
				opCode = -1;
				return true;

			case 4:
			case 44:
			case 84:
			case 101:
			case 105:
			case 117:
			case 147:
			case 151:
			case 156:
			case 160:
			case 215:
				method137(incoming, opCode);
				opCode = -1;
				return true;

			case 106:
				tabID = incoming.readNegUByte();
				tabAreaAltered = true;
				opCode = -1;
				return true;

			case 164:
				int j9 = incoming.readLEUShort();
				writeInterface(j9);
				if (overlayInterfaceId != -1) {
					overlayInterfaceId = -1;
					tabAreaAltered = true;
				}
				backDialogueId = j9;
				inputTaken = true;
				openInterfaceId = -1;
				continuedDialogue = false;
				opCode = -1;
				return true;

			}
			Signlink.reporterror("T1 - " + opCode + "," + packetSize + " - "
					+ secondLastOpcode + "," + thirdLastOpcode);
			// resetLogout();
		} catch (IOException _ex) {
			dropClient();
		} catch (Exception exception) {
			String s2 = "T2 - " + opCode + "," + secondLastOpcode + ","
					+ thirdLastOpcode + " - " + packetSize + ","
					+ (baseX + localPlayer.pathX[0]) + ","
					+ (baseY + localPlayer.pathY[0]) + " - ";
			for (int j15 = 0; j15 < packetSize && j15 < 50; j15++)
				s2 = s2 + incoming.payload[j15] + ",";
			Signlink.reporterror(s2);
			// resetLogout();
		}
		opCode = -1;
		return true;
	}

	private void moveCameraWithPlayer() {
		anInt1265++;
		showOtherPlayers(true);
		showNPCs(true);
		showOtherPlayers(false);
		showNPCs(false);
		createProjectiles();
		createStationaryGraphics();
		if (!aBoolean1160) {
			int i = anInt1184;
			if (anInt984 / 256 > i)
				i = anInt984 / 256;
			if (aBooleanArray876[4] && anIntArray1203[4] + 128 > i)
				i = anIntArray1203[4] + 128;
			int k = cameraHorizontal + anInt896 & 0x7ff;
			setCameraPos(cameraZoom
					+ i
					* ((SceneGraph.viewDistance == 9)
							&& (frameMode == ScreenMode.RESIZABLE) ? 2
							: SceneGraph.viewDistance == 10 ? 5 : 3), i,
					anInt1014,
					method42(plane, localPlayer.y, localPlayer.x) - 50, k,
					anInt1015);
		}
		int j;
		if (!aBoolean1160)
			j = setCameraLocation();
		else
			j = resetCameraHeight();
		int l = xCameraPos;
		int i1 = zCameraPos;
		int j1 = yCameraPos;
		int k1 = yCameraCurve;
		int l1 = xCameraCurve;
		for (int i2 = 0; i2 < 5; i2++)
			if (aBooleanArray876[i2]) {
				int j2 = (int) ((Math.random()
						* (double) (anIntArray873[i2] * 2 + 1) - (double) anIntArray873[i2]) + Math
						.sin((double) anIntArray1030[i2]
								* ((double) anIntArray928[i2] / 100D))
						* (double) anIntArray1203[i2]);
				if (i2 == 0)
					xCameraPos += j2;
				if (i2 == 1)
					zCameraPos += j2;
				if (i2 == 2)
					yCameraPos += j2;
				if (i2 == 3)
					xCameraCurve = xCameraCurve + j2 & 0x7ff;
				if (i2 == 4) {
					yCameraCurve += j2;
					if (yCameraCurve < 128)
						yCameraCurve = 128;
					if (yCameraCurve > 383)
						yCameraCurve = 383;
				}
			}
		int k2 = Rasterizer.anInt1481;
		Model.aBoolean1684 = true;
		Model.anInt1687 = 0;
		Model.anInt1685 = super.mouseX
				- (frameMode == ScreenMode.FIXED ? 4 : 0);
		Model.anInt1686 = super.mouseY
				- (frameMode == ScreenMode.FIXED ? 4 : 0);
		Raster.clear();
		worldController.method313(xCameraPos, yCameraPos, xCameraCurve,
				zCameraPos, j, yCameraCurve);
		worldController.clearObj5Cache();
		if (Configuration.enableFog) {
			double fogDistance = Math.sqrt(Math.pow(zCameraPos, 2));
			int fogStartDistance = 1330;
			int fogEndDistance = 2100;
			depthBuffer.setFogDistance((float) fogDistance);
			depthBuffer.renderFog(false, fogStartDistance, fogEndDistance, 3);
		}
		updateEntities();
		drawHeadIcon();
		writeBackgroundTexture(k2);
		draw3dScreen();
		if (frameMode != ScreenMode.FIXED) {
			drawChatArea();
			drawMinimap();
			drawTabArea();
		}
		gameScreenImageProducer.drawGraphics(frameMode == ScreenMode.FIXED ? 4
				: 0, super.graphics, frameMode == ScreenMode.FIXED ? 4 : 0);
		xCameraPos = l;
		zCameraPos = i1;
		yCameraPos = j1;
		yCameraCurve = k1;
		xCameraCurve = l1;
	}

	private void processMinimapActions() {
		final boolean fixed = frameMode == ScreenMode.FIXED;
		if (fixed ? super.mouseX >= 542 && super.mouseX <= 579
				&& super.mouseY >= 2 && super.mouseY <= 38
				: super.mouseX >= frameWidth - 180
						&& super.mouseX <= frameWidth - 139
						&& super.mouseY >= 0 && super.mouseY <= 40) {
			menuActionName[1] = "Face North";
			menuActionID[1] = 696;
			menuActionRow = 2;
		}
		if (frameMode != ScreenMode.FIXED && changeTabArea) {
			if (super.mouseX >= frameWidth - 26
					&& super.mouseX <= frameWidth - 1 && super.mouseY >= 2
					&& super.mouseY <= 24) {
				menuActionName[1] = "Logout";
				menuActionID[1] = 700;
				menuActionRow = 2;
			}
		}
		if (counterHover && Configuration.enableOrbs) {
			menuActionName[2] = counterOn ? "Hide @or1@ XP Drops"
					: "Show @or1@ XP Drops";
			menuActionID[2] = 474;
			menuActionName[1] = "Reset XP Total";
			menuActionID[1] = 475;
			menuActionRow = 3;
		}
		if (worldHover) {
			menuActionName[1] = "World Map";
			menuActionID[1] = 850;
			menuActionRow = 2;
		}
		if (specialHover) {
			menuActionName[1] = "Use Special Attack";
			menuActionID[1] = 851;
			menuActionRow = 2;
		}
		if (hpHover) {
			menuActionName[1] = Configuration.hpAboveHeads ? "Turn HP Above Heads on"
					: "Turn HP Above heads off";
			menuActionID[1] = 1508;
			menuActionRow = 2;
		}
		if (prayHover) {
			menuActionName[2] = prayClicked ? "Turn quick-prayers off"
					: "Turn quick-prayers on";
			menuActionID[2] = 1500;
			menuActionRow = 2;
			menuActionName[1] = "Select quick-prayers";
			menuActionID[1] = 1506;
			menuActionRow = 3;
		}
		if (runHover) {
			menuActionName[1] = !runClicked ? "Turn run mode on"
					: "Turn run mode off";
			menuActionID[1] = 1050;
			menuActionRow = 2;
		}
	}

	private void drawSpecialOrb() {
		String value = Widget.interfaceCache[155].defaultText;
		int spec = Integer.parseInt(value);
		if (specialHover) {
			cacheSprite[56].drawSprite(frameMode == ScreenMode.FIXED ? 153
					: frameWidth - 63, frameMode == ScreenMode.FIXED ? 131
					: 150);
		} else {
			cacheSprite[42].drawSprite(frameMode == ScreenMode.FIXED ? 154
					: frameWidth - 62, frameMode == ScreenMode.FIXED ? 132
					: 151);
		}
		cacheSprite[5].myHeight = (int) (spec * 27 / 100);
		cacheSprite[5].drawSprite(frameMode == ScreenMode.FIXED ? 157
				: frameWidth - 58, frameMode == ScreenMode.FIXED ? 135 : 155);
		cacheSprite[14].drawSprite(frameMode == ScreenMode.FIXED ? 157
				: frameWidth - 58, frameMode == ScreenMode.FIXED ? 135 : 155);
		cacheSprite[55].drawSprite(frameMode == ScreenMode.FIXED ? 162
				: frameWidth - 53, frameMode == ScreenMode.FIXED ? 140 : 160);
		smallText.method382(getOrbTextColor(spec),
				frameMode == ScreenMode.FIXED ? 198 : frameWidth - 19, Integer
						.toString(spec), frameMode == ScreenMode.FIXED ? 158
						: 177, true);
	}

	public boolean isPoisoned, clickedQuickPrayers;

	public final int[] // Perfected (HP ORB, PRAY ORB, RUN Orb)
			orbX = { 0, 0, 24 },
			orbY = { 41, 85, 122 }, orbTextX = { 15, 16, 40 }, orbTextY = { 67,
					111, 148 }, coloredOrbX = { 27, 27, 51 }, coloredOrbY = {
					45, 89, 126 },
			currentInterface = { 4016, 4012, 149 },
			maximumInterface = { 4017, 4013, 149 },
			orbIconX = { 33, 30, 58 },
			orbIconY = { 51, 92, 130 };

	private void loadAllOrbs(int xOffset) {
		if (Configuration.enableOrbs) {
			drawSpecialOrb();
			int[] spriteID = { !isPoisoned && hpHover ? 8 : 7,
					prayHover ? 8 : 7, runHover ? 8 : 7,
					sumActive && sumHover ? 8 : 7 }, coloredOrbSprite = { 0,
					clickedQuickPrayers ? 2 : 1, runClicked ? 4 : 3,
					sumActive ? 6 : 5 }, orbSprite = { 9, 10,
					(runClicked ? 12 : 11), 13 };
			String cEnergy = Widget.interfaceCache[149].defaultText.replaceAll(
					"%", "");
			String hp = Widget.interfaceCache[4016].defaultText.replaceAll("%",
					"");
			int currentHP = Integer.parseInt(hp), currentEnergy = Integer
					.parseInt(cEnergy);
			for (int i = 0; i < 3; i++) {
				String currentStats = Widget.interfaceCache[currentInterface[i]].defaultText
						.replaceAll("%", ""), maxStats = Widget.interfaceCache[maximumInterface[i]].defaultText
						.replaceAll("%", "");
				int currentLevel = Integer.parseInt(currentStats), maxLevel = Integer
						.parseInt(maxStats), level = (int) (((double) currentLevel / (double) maxLevel) * 100D);
				cacheSprite[spriteID[i]].drawSprite(orbX[i] + xOffset, orbY[i]);
				cacheSprite[coloredOrbSprite[i]].drawSprite(coloredOrbX[i]
						+ xOffset, coloredOrbY[i]);
				double percent = (i == 2 ? Configuration.runEnergy ? currentEnergy / 100D
						: 100
						: level / 100D), fillHp = 26 * percent, fillPrayer = 26 * percent, fillRun = 26 * percent;
				double[] fill = { fillHp, fillPrayer, fillRun };
				int depleteFill = 26 - (int) fill[i];
				cacheSprite[14].myHeight = depleteFill;
				try {
					cacheSprite[14].drawSprite(coloredOrbX[i] + xOffset,
							coloredOrbY[i]);
				} catch (Exception e) {
				}
				cacheSprite[orbSprite[i]].drawSprite(orbIconX[i] + xOffset,
						orbIconY[i]);
				if (Configuration.tenXHp) {
					smallText
							.method382(
									getOrbTextColor(i == 2 ? Configuration.runEnergy ? currentEnergy
											: 100
											: level),
									orbTextX[i] + xOffset,
									""
											+ (i == 2 ? Configuration.runEnergy ? cEnergy
													: 100
													: i == 0
															&& Configuration.newDamage ? currentHP * 10
															: Widget.interfaceCache[currentInterface[i]].defaultText
																	.replaceAll(
																			"%",
																			"")),
									orbTextY[i], true);
				} else {
					smallText
							.method382(
									getOrbTextColor(i == 2 ? Configuration.runEnergy ? currentEnergy
											: 100
											: level),
									orbTextX[i] + xOffset,
									""
											+ (i == 2 ? Configuration.runEnergy ? cEnergy
													: 100
													: i == 0
															&& Configuration.newDamage ? currentHP * 1
															: Widget.interfaceCache[currentInterface[i]].defaultText
																	.replaceAll(
																			"%",
																			"")),
									orbTextY[i], true);
				}
			}
			if (frameMode == ScreenMode.FIXED) {
				cacheSprite[worldHover ? 54 : 53].drawSprite(202, 20);
			} else {
				cacheSprite[worldHover ? 52 : 51].drawSprite(frameWidth - 118,
						154);
			}
		}
	}

	public int digits = 0;

	private void drawCounterOnScreen() {
		RSFont xp_font = newBoldFont;
		int font_height = 24;
		int x = frameMode == ScreenMode.FIXED ? 500 : frameWidth - 200;
		int y = frameMode == ScreenMode.FIXED ? 46 : 50;
		digits = xpCounter == 0 ? 1 : 1 + (int) Math.floor(Math
				.log10(xpCounter));
		int lengthToRemove = Integer.toString(xpCounter).length();
		int i = regularText.getTextWidth(Integer.toString(xpCounter))
				- regularText.getTextWidth(Integer.toString(xpCounter)) / 2;
		int a = lengthToRemove == 1 ? 5 : ((lengthToRemove - 1) * 5);

		for (i = 0; i < xp_added.length; i++) {
			if (xp_added[i][0] > -1) {
				if (xp_added[i][2] >= 0) {
					int transparency = 256;
					if (xp_added[i][2] > 120)
						transparency = (20 - (xp_added[i][2] - 120)) * 256 / 20;
					String s = "<col=4682B4><trans="
							+ transparency
							+ ">+"
							+ NumberFormat.getIntegerInstance().format(
									xp_added[i][1]);
					int icons_x_off = 0;
					Sprite sprite = null;
					for (int count = 0; count < skill_sprites.length; count++) {
						if ((xp_added[i][0] & (1 << count)) == 0)
							continue;

						sprite = skill_sprites[count];
						icons_x_off += sprite.myWidth + 3;
						sprite.drawSprite(x - a + 12 - xp_font.getTextWidth(s)
								- icons_x_off,
								y + (140 - xp_added[i][2]) - (font_height / 2)
										- (sprite.myHeight / 2) + 7,
								transparency);
					}
					xp_font.drawBasicString(s,
							x - a + 12 - xp_font.getTextWidth(s), y
									+ (140 - xp_added[i][2]), 0xffffff, -1);
				}

				xp_added[i][2]++;

				if (xp_added[i][2] >= 140) // 50
					xp_added[i][0] = -1;
			}
		}
	}

	public int xpCounter, xpAddedPos, expAdded;

	private boolean runHover, prayHover, hpHover, prayClicked, counterOn,
			sumHover, sumActive, counterHover, specialHover, worldHover,
			autocast, runClicked = true;

	public int getOrbTextColor(int statusInt) {
		if (statusInt >= 75 && statusInt <= Integer.MAX_VALUE)
			return 0x00FF00;
		else if (statusInt >= 50 && statusInt <= 74)
			return 0xFFFF00;
		else if (statusInt >= 25 && statusInt <= 49)
			return 0xFF981F;
		else
			return 0xFF0000;
	}

	public int getOrbFill(int statusInt) {
		if (statusInt <= Integer.MAX_VALUE && statusInt >= 97)
			return 0;
		else if (statusInt <= 96 && statusInt >= 93)
			return 1;
		else if (statusInt <= 92 && statusInt >= 89)
			return 2;
		else if (statusInt <= 88 && statusInt >= 85)
			return 3;
		else if (statusInt <= 84 && statusInt >= 81)
			return 4;
		else if (statusInt <= 80 && statusInt >= 77)
			return 5;
		else if (statusInt <= 76 && statusInt >= 73)
			return 6;
		else if (statusInt <= 72 && statusInt >= 69)
			return 7;
		else if (statusInt <= 68 && statusInt >= 65)
			return 8;
		else if (statusInt <= 64 && statusInt >= 61)
			return 9;
		else if (statusInt <= 60 && statusInt >= 57)
			return 10;
		else if (statusInt <= 56 && statusInt >= 53)
			return 11;
		else if (statusInt <= 52 && statusInt >= 49)
			return 12;
		else if (statusInt <= 48 && statusInt >= 45)
			return 13;
		else if (statusInt <= 44 && statusInt >= 41)
			return 14;
		else if (statusInt <= 40 && statusInt >= 37)
			return 15;
		else if (statusInt <= 36 && statusInt >= 33)
			return 16;
		else if (statusInt <= 32 && statusInt >= 29)
			return 17;
		else if (statusInt <= 28 && statusInt >= 25)
			return 18;
		else if (statusInt <= 24 && statusInt >= 21)
			return 19;
		else if (statusInt <= 20 && statusInt >= 17)
			return 20;
		else if (statusInt <= 16 && statusInt >= 13)
			return 21;
		else if (statusInt <= 12 && statusInt >= 9)
			return 22;
		else if (statusInt <= 8 && statusInt >= 7)
			return 23;
		else if (statusInt <= 6 && statusInt >= 5)
			return 24;
		else if (statusInt <= 4 && statusInt >= 3)
			return 25;
		else if (statusInt <= 2 && statusInt >= 1)
			return 26;
		else if (statusInt <= 0)
			return 27;
		return 0;
	}

	public void clearTopInterfaces() {
		outgoing.createFrame(130);
		if (overlayInterfaceId != -1) {
			overlayInterfaceId = -1;
			continuedDialogue = false;
			tabAreaAltered = true;
		}
		if (backDialogueId != -1) {
			backDialogueId = -1;
			inputTaken = true;
			continuedDialogue = false;
		}
		openInterfaceId = -1;
		fullscreenInterfaceID = -1;
	}

	private int currentTrackPlaying;

	public Game() {
		xpAddedPos = xpCounter = expAdded = 0;
		fullscreenInterfaceID = -1;
		chatRights = new int[500];
		soundVolume = new int[50];
		chatTypeView = 0;
		clanChatMode = 0;
		cButtonHPos = -1;
		currentTrackPlaying = -1;
		cButtonCPos = 0;
		server = Configuration.server_address;
		anIntArrayArray825 = new int[104][104];
		friendsNodeIDs = new int[200];
		groundItems = new Deque[4][104][104];
		aBoolean831 = false;
		aStream_834 = new Buffer(new byte[5000]);
		npcs = new Npc[16384];
		npcIndices = new int[16384];
		anIntArray840 = new int[1000];
		login = Buffer.create();
		aBoolean848 = true;
		openInterfaceId = -1;
		currentExp = new int[SkillConstants.skillsCount];
		aBoolean872 = false;
		anIntArray873 = new int[5];
		aBooleanArray876 = new boolean[5];
		drawFlames = false;
		reportAbuseInput = "";
		unknownInt10 = -1;
		menuOpen = false;
		inputString = "";
		maxPlayers = 2048;
		internalLocalPlayerIndex = 2047;
		players = new Player[maxPlayers];
		playerIndices = new int[maxPlayers];
		anIntArray894 = new int[maxPlayers];
		playerSynchronizationBuffers = new Buffer[maxPlayers];
		anInt897 = 1;
		anIntArrayArray901 = new int[104][104];
		aByteArray912 = new byte[16384];
		currentStats = new int[SkillConstants.skillsCount];
		ignoreListAsLongs = new long[100];
		loadingError = false;
		anIntArray928 = new int[5];
		anIntArrayArray929 = new int[104][104];
		chatTypes = new int[500];
		chatNames = new String[500];
		chatMessages = new String[500];
		sideIcons = new Sprite[15];
		aBoolean954 = true;
		friendsListAsLongs = new long[200];
		currentSong = -1;
		drawingFlames = false;
		spriteDrawX = -1;
		spriteDrawY = -1;
		anIntArray968 = new int[33];
		anIntArray969 = new int[256];
		decompressors = new Index[5];
		variousSettings = new int[2000];
		aBoolean972 = false;
		anInt975 = 50;
		anIntArray976 = new int[anInt975];
		anIntArray977 = new int[anInt975];
		anIntArray978 = new int[anInt975];
		anIntArray979 = new int[anInt975];
		textColourEffect = new int[anInt975];
		anIntArray981 = new int[anInt975];
		anIntArray982 = new int[anInt975];
		aStringArray983 = new String[anInt975];
		anInt985 = -1;
		hitMarks = new Sprite[20];
		characterDesignColours = new int[5];
		aBoolean994 = false;
		amountOrNameInput = "";
		projectiles = new Deque();
		aBoolean1017 = false;
		openWalkableInterface = -1;
		anIntArray1030 = new int[5];
		aBoolean1031 = false;
		mapFunctions = new Sprite[100];
		dialogueId = -1;
		maxStats = new int[SkillConstants.skillsCount];
		anIntArray1045 = new int[2000];
		maleCharacter = true;
		minimapLeft = new int[152];
		minimapLineWidth = new int[152];
		flashingSidebarId = -1;
		incompleteAnimables = new Deque();
		anIntArray1057 = new int[33];
		aClass9_1059 = new Widget();
		mapScenes = new Background[100];
		barFillColor = 0x4d4233;
		anIntArray1065 = new int[7];
		minimapHintX = new int[1000];
		minimapHintY = new int[1000];
		aBoolean1080 = false;
		friendsList = new String[200];
		incoming = Buffer.create();
		archiveCRCs = new int[9];
		menuActionCmd2 = new int[500];
		menuActionCmd3 = new int[500];
		menuActionID = new int[500];
		menuActionCmd1 = new int[500];
		headIcons = new Sprite[20];
		skullIcons = new Sprite[20];
		headIconsHint = new Sprite[20];
		tabAreaAltered = false;
		aString1121 = "";
		atPlayerActions = new String[5];
		atPlayerArray = new boolean[5];
		anIntArrayArrayArray1129 = new int[4][13][13];
		anInt1132 = 2;
		minimapHint = new Sprite[1000];
		aBoolean1141 = false;
		continuedDialogue = false;
		crosses = new Sprite[8];
		Configuration.enableMusic = true;
		loggedIn = false;
		canMute = false;
		aBoolean1159 = false;
		aBoolean1160 = false;
		anInt1171 = 1;
		myUsername = "mod wind";
		myPassword = "test";
		genericLoadingError = false;
		reportAbuseInterfaceID = -1;
		spawns = new Deque();
		anInt1184 = 128;
		overlayInterfaceId = -1;
		outgoing = Buffer.create();
		menuActionName = new String[500];
		anIntArray1203 = new int[5];
		tracks = new int[50];
		anInt1210 = 2;
		anInt1211 = 78;
		promptInput = "";
		modIcons = new Sprite[2];
		tabID = 3;
		inputTaken = false;
		songChanging = true;
		aClass11Array1230 = new CollisionMap[4];
		anIntArray1240 = new int[100];
		trackLoops = new int[50];
		aBoolean1242 = false;
		soundDelay = new int[50];
		rsAlreadyLoaded = false;
		welcomeScreenRaised = false;
		messagePromptRaised = false;
		firstLoginMessage = "";
		secondLoginMessage = "";
		backDialogueId = -1;
		anInt1279 = 2;
		bigX = new int[4000];
		bigY = new int[4000];
	}

	public int rights;
	public String name;
	public String defaultText;
	public String clanname;
	private final int[] chatRights;
	public int chatTypeView;
	public int clanChatMode;
	public int autoCastId = 0;
	public static Sprite[] cacheSprite;
	private ImageProducer leftFrame;
	private ImageProducer topFrame;
	private int ignoreCount;
	private long aLong824;
	private int[][] anIntArrayArray825;
	private int[] friendsNodeIDs;
	private Deque[][][] groundItems;
	private int[] anIntArray828;
	private int[] anIntArray829;
	private volatile boolean aBoolean831;
	private Socket aSocket832;
	private int loginScreenState;
	private Buffer aStream_834;
	private Npc[] npcs;
	private int npcCount;
	private int[] npcIndices;
	private int anInt839;
	private int[] anIntArray840;
	private int lastOpcode;
	private int secondLastOpcode;
	private int thirdLastOpcode;
	private String clickToContinueString;
	public String prayerBook;
	private int privateChatMode;
	private Buffer login;
	private boolean aBoolean848;
	private static int anInt849;
	private int[] anIntArray850;
	private int[] anIntArray851;
	private int[] anIntArray852;
	private int[] anIntArray853;
	private static int anInt854;
	private int hintIconDrawType;
	static int openInterfaceId;
	private int xCameraPos;
	private int zCameraPos;
	private int yCameraPos;
	private int yCameraCurve;
	private int xCameraCurve;
	private int myPrivilege;
	private final int[] currentExp;
	private Sprite mapFlag;
	private Sprite mapMarker;
	private boolean aBoolean872;
	private final int[] anIntArray873;
	private final boolean[] aBooleanArray876;
	private int weight;
	private MouseDetection mouseDetection;
	private volatile boolean drawFlames;
	private String reportAbuseInput;
	private int unknownInt10;
	private boolean menuOpen;
	private int anInt886;
	private String inputString;
	private final int maxPlayers;
	private final int internalLocalPlayerIndex;
	private Player[] players;
	private int playerCount;
	private int[] playerIndices;
	private int anInt893;
	private int[] anIntArray894;
	private Buffer[] playerSynchronizationBuffers;
	private int anInt896;
	public int anInt897;
	private int friendsCount;
	private int friendServerStatus;
	private int[][] anIntArrayArray901;
	private byte[] aByteArray912;
	private int anInt913;
	private int crossX;
	private int crossY;
	private int crossIndex;
	private int crossType;
	private int plane;
	private final int[] currentStats;
	private static int anInt924;
	private final long[] ignoreListAsLongs;
	private boolean loadingError;
	private final int[] anIntArray928;
	private int[][] anIntArrayArray929;
	private Sprite aClass30_Sub2_Sub1_Sub1_931;
	private Sprite aClass30_Sub2_Sub1_Sub1_932;
	private int hintIconPlayerId;
	private int hintIconX;
	private int hintIconY;
	private int anInt936;
	private int anInt937;
	private int anInt938;
	private final int[] chatTypes;
	private final String[] chatNames;
	private final String[] chatMessages;
	private int anInt945;
	private SceneGraph worldController;
	private Sprite[] sideIcons;
	private int menuScreenArea;
	private int menuOffsetX;
	private int menuOffsetY;
	private int menuWidth;
	private int menuHeight;
	private long aLong953;
	private boolean aBoolean954;
	private long[] friendsListAsLongs;
	private String[] clanList = new String[100];
	private int currentSong;
	private static int nodeID = 10;
	public static int portOff;
	private static boolean isMembers = true;
	private static boolean lowMem;
	private volatile boolean drawingFlames;
	private int spriteDrawX;
	private int spriteDrawY;
	private final int[] anIntArray965 = { 0xffff00, 0xff0000, 65280, 65535,
			0xff00ff, 0xffffff };
	private Background aBackground_966;
	private Background aBackground_967;
	private final int[] anIntArray968;
	private final int[] anIntArray969;
	public final Index[] decompressors;
	public int variousSettings[];
	private boolean aBoolean972;
	private final int anInt975;
	private final int[] anIntArray976;
	private final int[] anIntArray977;
	private final int[] anIntArray978;
	private final int[] anIntArray979;
	private final int[] textColourEffect;
	private final int[] anIntArray981;
	private final int[] anIntArray982;
	private final String[] aStringArray983;
	private int anInt984;
	private int anInt985;
	private static int anInt986;
	private Sprite[] hitMarks;
	public int anInt988;
	private int anInt989;
	private final int[] characterDesignColours;
	private final boolean aBoolean994;
	private int anInt995;
	private int anInt996;
	private int anInt997;
	private int anInt998;
	private int anInt999;
	private ISAACCipher encryption;
	private Sprite multiOverlay;
	public static final int[][] anIntArrayArray1003 = {
			{ 6798, 107, 10283, 16, 4797, 7744, 5799, 4634, 33697, 22433, 2983,
					54193 },
			{ 8741, 12, 64030, 43162, 7735, 8404, 1701, 38430, 24094, 10153,
					56621, 4783, 1341, 16578, 35003, 25239 },
			{ 25238, 8742, 12, 64030, 43162, 7735, 8404, 1701, 38430, 24094,
					10153, 56621, 4783, 1341, 16578, 35003 },
			{ 4626, 11146, 6439, 12, 4758, 10270 },
			{ 4550, 4537, 5681, 5673, 5790, 6806, 8076, 4574 } };
	private String amountOrNameInput;
	private static int anInt1005;
	private int daysSinceLastLogin;
	private int packetSize;
	private int opCode;
	private int timeoutCounter;
	private int anInt1010;
	private int anInt1011;
	private Deque projectiles;
	private int anInt1014;
	private int anInt1015;
	private int anInt1016;
	private boolean aBoolean1017;
	private int openWalkableInterface;
	private static final int[] anIntArray1019;
	private int minimapState;
	private int duplicateClickCount;
	private int loadingStage;
	private Sprite scrollBar1;
	private Sprite scrollBar2;
	private int anInt1026;
	private final int[] anIntArray1030;
	private boolean aBoolean1031;
	private Sprite[] mapFunctions;
	private int baseX;
	private int baseY;
	private int anInt1036;
	private int anInt1037;
	private int loginFailures;
	private int anInt1039;
	private int anInt1040;
	private int anInt1041;
	private int dialogueId;
	private final int[] maxStats;
	private final int[] anIntArray1045;
	private int anInt1046;
	private boolean maleCharacter;
	private int anInt1048;
	private String aString1049;
	private static int anInt1051;
	private final int[] minimapLeft;
	private CacheArchive titleStreamLoader;
	private int flashingSidebarId;
	private int multicombat;
	private Deque incompleteAnimables;
	private final int[] anIntArray1057;
	public final Widget aClass9_1059;
	private Background[] mapScenes;
	private int trackCount;
	private final int barFillColor;
	private int friendsListAction;
	private final int[] anIntArray1065;
	private int mouseInvInterfaceIndex;
	private int lastActiveInvInterface;
	public OnDemandRequester onDemandFetcher;
	private int anInt1069;
	private int anInt1070;
	private int anInt1071;
	private int[] minimapHintX;
	private int[] minimapHintY;
	private Sprite mapDotItem;
	private Sprite mapDotNPC;
	private Sprite mapDotPlayer;
	private Sprite mapDotFriend;
	private Sprite mapDotTeam;
	private Sprite mapDotClan;
	private int anInt1079;
	private boolean aBoolean1080;
	private String[] friendsList;
	private Buffer incoming;
	private int anInt1084;
	private int anInt1085;
	private int activeInterfaceType;
	private int anInt1087;
	private int anInt1088;
	public static int anInt1089;
	public static int spellID = 0;
	public static int totalRead = 0;
	private final int[] archiveCRCs;
	private int[] menuActionCmd2;
	private int[] menuActionCmd3;
	private int[] menuActionID;
	private int[] menuActionCmd1;
	private Sprite[] headIcons;
	private Sprite[] skullIcons;
	private Sprite[] headIconsHint;
	private static int anInt1097;
	private int x;
	private int y;
	private int height;
	private int speed;
	private int angle;
	private static boolean tabAreaAltered;
	private int systemUpdateTime;
	private ImageProducer topLeft1BackgroundTile;
	private ImageProducer bottomLeft1BackgroundTile;
	private static ImageProducer loginBoxImageProducer;
	private ImageProducer flameLeftBackground;
	private ImageProducer flameRightBackground;
	private ImageProducer bottomLeft0BackgroundTile;
	private ImageProducer bottomRightImageProducer;
	private ImageProducer loginMusicImageProducer;
	private ImageProducer middleLeft1BackgroundTile;
	private ImageProducer aRSImageProducer_1115;
	private static int anInt1117;
	private int membersInt;
	private String aString1121;
	private Sprite compass;
	private ImageProducer chatSettingImageProducer;
	public static Player localPlayer;
	private final String[] atPlayerActions;
	private final boolean[] atPlayerArray;
	private final int[][][] anIntArrayArrayArray1129;
	public static final int[] tabInterfaceIDs = { -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 };
	private int anInt1131;
	public int anInt1132;
	private int menuActionRow;
	private static int anInt1134;
	private int spellSelected;
	private int anInt1137;
	private int spellUsableOn;
	private String spellTooltip;
	private Sprite[] minimapHint;
	private boolean aBoolean1141;
	private static int anInt1142;
	private int energy;
	private boolean continuedDialogue;
	private Sprite[] crosses;
	private Background[] aBackgroundArray1152s;
	private int unreadMessages;
	private static int anInt1155;
	private static boolean fpsOn;
	public static boolean loggedIn;
	private boolean canMute;
	private boolean aBoolean1159;
	private boolean aBoolean1160;
	public static int loopCycle;
	private static final String validUserPassChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"\243$%^&*()-_=+[{]};:'@#~,<.>/?\\| ";
	private static ImageProducer tabImageProducer;
	private ImageProducer minimapImageProducer;
	private static ImageProducer gameScreenImageProducer;
	private static ImageProducer chatboxImageProducer;
	private int daysSinceRecovChange;
	private RSSocket socketStream;
	private int anInt1169;
	private int minimapZoom;
	public int anInt1171;
	private String myUsername;
	private String myPassword;
	private static int anInt1175;
	private boolean genericLoadingError;
	private final int[] anIntArray1177 = { 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2, 2,
			2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3 };
	private int reportAbuseInterfaceID;
	private Deque spawns;
	private static int[] anIntArray1180;
	private static int[] anIntArray1181;
	private static int[] anIntArray1182;
	private byte[][] aByteArrayArray1183;
	private int anInt1184;
	private int cameraHorizontal;
	private int anInt1186;
	private int anInt1187;
	private static int anInt1188;
	private int overlayInterfaceId;
	private int[] anIntArray1190;
	private int[] anIntArray1191;
	private Buffer outgoing;
	private int anInt1193;
	private int splitPrivateChat;
	private Background mapBack;
	private String[] menuActionName;
	private Sprite aClass30_Sub2_Sub1_Sub1_1201;
	private Sprite aClass30_Sub2_Sub1_Sub1_1202;
	private final int[] anIntArray1203;
	public static final int[] anIntArray1204 = { 9104, 10275, 7595, 3610, 7975,
			8526, 918, 38802, 24466, 10145, 58654, 5027, 1457, 16565, 34991,
			25486 };
	private static boolean flagged;
	private final int[] tracks;
	private int minimapRotation;
	public int anInt1210;
	static int anInt1211;
	private String promptInput;
	private int anInt1213;
	private int[][][] intGroundArray;
	private long serverSeed;
	private int loginScreenCursorPos;
	private final Sprite[] modIcons;
	private long aLong1220;
	static int tabID;
	private int hintIconNpcId;
	public static boolean inputTaken;
	private int inputDialogState;
	private static int anInt1226;
	private int nextSong;
	private boolean songChanging;
	private final int[] minimapLineWidth;
	private CollisionMap[] aClass11Array1230;
	public static int BIT_MASKS[];
	private int[] anIntArray1234;
	private int[] anIntArray1235;
	private int[] anIntArray1236;
	private int anInt1237;
	private int anInt1238;
	public final int anInt1239 = 100;
	private final int[] anIntArray1240;
	private final int[] trackLoops;
	private boolean aBoolean1242;
	private int atInventoryLoopCycle;
	private int atInventoryInterface;
	private int atInventoryIndex;
	private int atInventoryInterfaceType;
	private byte[][] aByteArrayArray1247;
	private int tradeMode;
	private int anInt1249;
	private final int[] soundDelay;
	private int anInt1251;
	private final boolean rsAlreadyLoaded;
	private int anInt1253;
	public int anInt1254;
	private boolean welcomeScreenRaised;
	private boolean messagePromptRaised;
	private byte[][][] byteGroundArray;
	private int prevSong;
	private int destinationX;
	private int destY;
	private Sprite minimapImage;
	private int anInt1264;
	private int anInt1265;
	private String firstLoginMessage;
	private String secondLoginMessage;
	private int anInt1268;
	private int anInt1269;
	private GameFont smallText;
	private GameFont regularText;
	private GameFont boldText;
	public RSFont newSmallFont, newRegularFont, newBoldFont;
	public RSFont newFancyFont;
	private int anInt1275;
	private int backDialogueId;
	private int anInt1278;
	public int anInt1279;
	private int[] bigX;
	private int[] bigY;
	private int itemSelected;
	private int anInt1283;
	private int anInt1284;
	private int anInt1285;
	private String selectedItemName;
	private int publicChatMode;
	private static int anInt1288;
	public static int anInt1290;
	public static String server = "";
	public int drawCount;
	public int fullscreenInterfaceID;
	public int anInt1044;// 377
	public int anInt1129;// 377
	public int anInt1315;// 377
	public int anInt1500;// 377
	public int anInt1501;// 377
	public static int[] fullScreenTextureArray;

	public void resetAllImageProducers() {
		if (super.fullGameScreen != null) {
			return;
		}
		chatboxImageProducer = null;
		minimapImageProducer = null;
		tabImageProducer = null;
		gameScreenImageProducer = null;
		chatSettingImageProducer = null;
		topLeft1BackgroundTile = null;
		bottomLeft1BackgroundTile = null;
		loginBoxImageProducer = null;
		flameLeftBackground = null;
		flameRightBackground = null;
		bottomLeft0BackgroundTile = null;
		bottomRightImageProducer = null;
		loginMusicImageProducer = null;
		middleLeft1BackgroundTile = null;
		aRSImageProducer_1115 = null;
		super.fullGameScreen = new ImageProducer(765, 503);
		welcomeScreenRaised = true;
	}

	public void mouseWheelDragged(int i, int j) {
		if (!mouseWheelDown) {
			return;
		}
		this.anInt1186 += i * 3;
		this.anInt1187 += (j << 1);
	}

	public void launchURL(String url) {
		String osName = System.getProperty("os.name");
		try {
			if (osName.startsWith("Mac OS")) {
				Class<?> fileMgr = Class.forName("com.apple.eio.FileManager");
				Method openURL = fileMgr.getDeclaredMethod("openURL",
						new Class[] { String.class });
				openURL.invoke(null, new Object[] { url });
			} else if (osName.startsWith("Windows"))
				Runtime.getRuntime().exec(
						"rundll32 url.dll,FileProtocolHandler " + url);
			else { // assume Unix or Linux
				String[] browsers = { "firefox", "opera", "konqueror",
						"epiphany", "mozilla", "netscape", "safari" };
				String browser = null;
				for (int count = 0; count < browsers.length && browser == null; count++)
					if (Runtime.getRuntime()
							.exec(new String[] { "which", browsers[count] })
							.waitFor() == 0)
						browser = browsers[count];
				if (browser == null) {
					throw new Exception("Could not find web browser");
				} else
					Runtime.getRuntime().exec(new String[] { browser, url });
			}
		} catch (Exception e) {
			pushMessage("Failed to open URL.", 0, "");
		}
	}

	static {
		anIntArray1019 = new int[99];
		int i = 0;
		for (int j = 0; j < 99; j++) {
			int l = j + 1;
			int i1 = (int) ((double) l + 300D * Math.pow(2D, (double) l / 7D));
			i += i1;
			anIntArray1019[j] = i / 4;
		}
		BIT_MASKS = new int[32];
		i = 2;
		for (int k = 0; k < 32; k++) {
			BIT_MASKS[k] = i - 1;
			i += i;
		}
	}
}