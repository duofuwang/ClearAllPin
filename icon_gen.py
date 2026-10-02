from PIL import Image, ImageDraw

S = 192
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
# 深色圆角方块 + 白色 X
d.rounded_rectangle([4, 4, S - 4, S - 4], radius=40, fill=(32, 34, 39, 255))
m = 58
w = 16
d.line([m, m, S - m, S - m], fill=(242, 242, 242, 255), width=w)
d.line([S - m, m, m, S - m], fill=(242, 242, 242, 255), width=w)
img.save(r"E:\Downloads\clearall-module\res\mipmap-xxhdpi\ic_launcher.png")
print("icon ok")
