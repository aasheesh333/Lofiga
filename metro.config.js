const { getDefaultConfig } = require("expo/metro-config");
const { withNativeWind } = require("nativewind/metro");

const config = getDefaultConfig(__dirname);

// Optimize transformer to prevent bundling timeouts
config.transformer = {
    ...config.transformer,
    minifierConfig: {
        keep_classnames: true,
        keep_fnames: true,
        mangle: {
            keep_classnames: true,
            keep_fnames: true,
        },
    },
};

// Increase server timeout for CI builds
config.server = {
    ...config.server,
    enhanceMiddleware: (middleware) => {
        return (req, res, next) => {
            res.setTimeout(600000); // 10 minutes
            return middleware(req, res, next);
        };
    },
};

module.exports = withNativeWind(config, { input: "./global.css" });
