// Mirrors RingNearbyProjection: unit arc spacing nearby, positive spacing everywhere.
float ring_trial_angle(float delta, float cameraY) {
    float circumference = float(RingWorldLayout.y);
    float u = mod(delta + circumference * 0.5, circumference) - circumference * 0.5;
    float a = abs(u), core = RingWorldDistortion.y, end = RingWorldDistortion.z;
    float nearSlope = 1.0 / (RingWorldDistortion.w + RingWorldVertical.x - cameraY);
    float midpoint = (core + end) * 0.5;
    float farSlope = (3.141592653589793 - nearSlope * midpoint) / (circumference * 0.5 - midpoint);
    float length = end - core;
    float value;
    if (a <= core) value = nearSlope * a;
    else if (a < end) {
        float t = (a - core) / length;
        value = nearSlope * a + (farSlope - nearSlope) * length * (t*t*t - 0.5*t*t*t*t);
    } else value = nearSlope * midpoint + farSlope * (length * 0.5 + a - end);
    return sign(u) * value;
}
vec3 ring_trial_position(vec3 point, vec3 camera) {
    float angle = ring_trial_angle(point.x - camera.x, camera.y);
    float radius = RingWorldDistortion.w + RingWorldVertical.x - point.y;
    float cameraRadius = RingWorldDistortion.w + RingWorldVertical.x - camera.y;
    return vec3(radius * sin(angle), cameraRadius - radius * cos(angle), point.z - camera.z);
}
