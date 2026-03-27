sa-token默认键数据保存在内存中，可以避免序列化/反序列化操作。这里用到了redis。所以让sa-token整合redis，将用户登录态等保存在redis中。

此处选择jackson序列化方式整合 redis，这样存到 redis 的数据是可读的。

