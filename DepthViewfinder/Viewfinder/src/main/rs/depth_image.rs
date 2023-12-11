#pragma version(1)
#pragma rs java_package_name(org.itri.example.depthviewfinder)
#pragma rs_fp_relaxed

//
// Find the min and max range value in the Depth16 image
//
#pragma rs reduce(findMinAndMax) \
    initializer(fMMInit) accumulator(fMMAccumulator) combiner(fMMCombiner)

//#define DEPTH16_RANGE_MIN       (ushort)(0x0000)
//#define DEPTH16_RANGE_MAX       (ushort)(0x1FFF)
//#define DEPTH16_CONFIDENCE_MIN  (ushort)(0x0000)
//#define DEPTH16_CONFIDENCE_MAX  (ushort)(0x0007)
const ushort DEPTH16_RANGE_MIN = 0x0000;
const ushort DEPTH16_RANGE_MAX = 0x1FFF;
const ushort DEPTH16_CONFIDENCE_MIN = 0x0000;
const ushort DEPTH16_CONFIDENCE_MAX = 0x0007;

static void fMMInit(ushort2 *accum) {
    accum->s0 = DEPTH16_RANGE_MAX; // s0 is min value
    accum->s1 = DEPTH16_RANGE_MIN; // s1 is max value
}

static void fMMAccumulator(ushort2 *accum, ushort in) {
    ushort depthRange = in & 0x1FFF;
    ushort depthConfidence = (in >> 13) & 0x7;

    // Filter out values that have invalid range values or zero confidence
    if (depthRange == 0 || depthConfidence == 1) return;

    if (depthRange < accum->s0) {
        accum->s0 = depthRange;
    }

    if (depthRange > accum->s1) {
        accum->s1 = depthRange;
    }
}

static void fMMCombiner(ushort2 *accum, const ushort2 *val) {
    if (val->s0 < accum->s0) {
        accum->s0 = val->s0;
    }

    if (val->s1 > accum->s1) {
        accum->s1 = val->s1;
    }
}

//
// Convert Depth16 image to the normalized RGBA image
//
const int DEPTH_IMAGE_MODE_GRAY = 0;
const int DEPTH_IMAGE_MODE_COLOR = 1;
const int DEPTH_IMAGE_MODE_CONFIDENCE = 2;

ushort gMinDepthRange = DEPTH16_RANGE_MIN;
ushort gMaxDepthRange = DEPTH16_RANGE_MAX;
ushort gConfidenceThreshold = 0;
int gDepthImageMode = DEPTH_IMAGE_MODE_GRAY;

uchar4 RS_KERNEL convertToRGBA(ushort in, uint32_t x, uint32_t y) {
    ushort depthRange = in & 0x1FFF;
    ushort depthConfidenceRaw = (in >> 13) & 0x7;
    float3 out;

    // depthConfidenceRaw: a value of 0 representing 100% confidence, a value of 1 representing 0% confidence,
    // a value of 2 representing 1/7, a value of 3 representing 2/7, and so on.
    //
    // depthConfidence: a value of 0 representing 0% confidence, a value of 1 representing 1/7 confidence,
    // a value of 2 representing 2/7, a value of 3 representing 3/7, and so on.
    ushort depthConfidence = (depthConfidenceRaw == 0) ? DEPTH16_CONFIDENCE_MAX : depthConfidenceRaw - 1;

    if ((depthConfidence == DEPTH16_CONFIDENCE_MAX) || (depthConfidence >= gConfidenceThreshold)) {
        // normalize depth range
        out.g = (gDepthImageMode == DEPTH_IMAGE_MODE_CONFIDENCE)
                    ? 0
                    : clamp(((float)depthRange - gMinDepthRange) / ((float)gMaxDepthRange - gMinDepthRange), 0.0f, 1.0f);

        // normalize depth confidence
        out.r = (gDepthImageMode == DEPTH_IMAGE_MODE_GRAY)
                    ? out.g
                    : clamp(((float)depthConfidence - DEPTH16_CONFIDENCE_MIN) / ((float)DEPTH16_CONFIDENCE_MAX - DEPTH16_CONFIDENCE_MIN), 0.0f, 1.0f);

        out.b = (gDepthImageMode == DEPTH_IMAGE_MODE_GRAY)
                    ? out.g
                    : 0;
    }
    else {
        out = (float3){ 0.0f, 0.0f, 0.0f };
    }

    return rsPackColorTo8888(out);
}