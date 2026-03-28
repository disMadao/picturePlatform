const fs = require("fs");
const path = require("path");
const root = "d:/code/picturePlatform";
function w(rel, c) {
  const fp = path.join(root, rel);
  fs.mkdirSync(path.dirname(fp), { recursive: true });
  fs.writeFileSync(fp, c, "utf8");
}
