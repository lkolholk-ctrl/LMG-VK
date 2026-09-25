#include "lmg/automix/pcm_owner_ingress.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>
#include <stdexcept>
namespace lmg::automix {
PcmOwnerIngress::PcmOwnerIngress(std::uint32_t channels, OwnerEncoding encoding,
                               std::uint32_t capacity)
    : channels_(channels), capacity_(capacity), encoding_(encoding) {
  if (channels < 1 || channels > 2 || capacity < 1 || capacity > maximumCapacity ||
      (encoding != OwnerEncoding::pcm16le && encoding != OwnerEncoding::float32le))
    throw std::invalid_argument("Invalid owner ingress configuration");
  static_assert(sizeof(float) == 4 && std::numeric_limits<float>::is_iec559, "IEEE float32 required");
  for (auto& q : queues_) {
    q.samples.resize(static_cast<std::size_t>(capacity) * channels);
    q.staging.resize(static_cast<std::size_t>(capacity) * channels);
  }
}
void PcmOwnerIngress::owner() {
  const auto current = std::this_thread::get_id();
  if (!ownerBound_) { owner_ = current; ownerBound_ = true; }
  else if (owner_ != current) throw std::logic_error("Owner ingress thread mismatch");
}
PcmOwnerIngress::Queue& PcmOwnerIngress::side(unsigned index) {
  if (index > 1) throw std::invalid_argument("Invalid owner side");
  return queues_[index];
}
float PcmOwnerIngress::sample(const std::uint8_t* p) const noexcept {
  if (encoding_ == OwnerEncoding::pcm16le) {
    const auto bits = std::uint32_t(p[0]) | (std::uint32_t(p[1]) << 8);
    const auto value = bits >= 32768 ? std::int32_t(bits) - 65536 : std::int32_t(bits);
    return static_cast<float>(value) / 32768.0f;
  }
  const std::uint32_t bits = std::uint32_t(p[0]) | (std::uint32_t(p[1]) << 8) |
      (std::uint32_t(p[2]) << 16) | (std::uint32_t(p[3]) << 24);
  float value; std::memcpy(&value, &bits, sizeof(value)); return value;
}
std::uint32_t PcmOwnerIngress::validate(const std::uint8_t* data, std::size_t bytes,
                                      std::int64_t first, std::uint32_t limit) const {
  const auto width = static_cast<unsigned>(encoding_);
  const auto frameBytes = width * channels_;
  if (bytes % frameBytes || bytes / frameBytes > limit || (bytes && !data) ||
      first < 0 || first > maximumFrame || bytes / frameBytes > std::uint64_t(maximumFrame - first))
    throw std::invalid_argument("Invalid owner PCM range");
  for (std::size_t i = 0; i < bytes; i += width)
    if (!std::isfinite(sample(data + i))) throw std::invalid_argument("Nonfinite owner PCM");
  return static_cast<std::uint32_t>(bytes / frameBytes);
}
void PcmOwnerIngress::stage(std::uint64_t ticket, std::int64_t generation,
                           std::int64_t revision, OwnerInput a, OwnerInput b) {
  owner();
  if (phase_ != OwnerIngressPhase::empty) throw std::logic_error("Owner already staged or committed");
  if (!ticket || generation <= 0 || revision < 0)
    throw std::invalid_argument("Invalid owner transaction identity");
  const std::array<OwnerInput, 2> inputs{a,b};
  std::array<std::uint32_t, 2> sizes{};
  // Validate both sides before mutating any transaction/staging metadata.
  for (unsigned s = 0; s < 2; ++s) {
    sizes[s] = validate(inputs[s].data, inputs[s].bytes, inputs[s].firstFrame, capacity_);
    if (!sizes[s] || inputs[s].cueFrame < inputs[s].firstFrame ||
        inputs[s].cueFrame >= inputs[s].firstFrame + sizes[s])
      throw std::invalid_argument("Cue is not inside owner initial block");
  }
  for (unsigned s = 0; s < 2; ++s) {
    auto& q = queues_[s]; const auto& in = inputs[s];
    for (std::size_t i = 0; i < static_cast<std::size_t>(sizes[s]) * channels_; ++i)
      q.staging[i] = sample(in.data + i * static_cast<unsigned>(encoding_));
    q.stagedFirst = in.firstFrame; q.stagedCue = in.cueFrame; q.stagedFrames = sizes[s];
  }
  ticket_ = ticket; generation_ = generation; revision_ = revision;
  phase_ = OwnerIngressPhase::staged;
}
bool PcmOwnerIngress::commit(std::uint64_t ticket) {
  owner();
  if (phase_ != OwnerIngressPhase::staged || ticket != ticket_) return false;
  for (auto& q : queues_) {
    q.samples.swap(q.staging); q.head = 0;
    q.stats = {q.stagedFirst, q.stagedFirst + q.stagedFrames, q.stagedCue,
               q.stagedFrames, 0, 0, q.stagedFrames};
  }
  phase_ = OwnerIngressPhase::committed;
  return true;
}
bool PcmOwnerIngress::abort(std::uint64_t ticket) {
  owner();
  if (!ticket || ticket != ticket_) return false;
  if (phase_ == OwnerIngressPhase::staged) {
    for (auto& q : queues_) q.stagedFrames = 0;
    ticket_ = 0; phase_ = OwnerIngressPhase::empty; return true;
  }
  if (phase_ == OwnerIngressPhase::committed) {
    for (auto& q : queues_) {
      q.stats.discarded += q.stats.queued; q.stats.queued = 0;
      q.stats.nextReadFrame = q.stats.nextWriteFrame;
    }
    phase_ = OwnerIngressPhase::aborted; return true;
  }
  return false;
}
std::uint32_t PcmOwnerIngress::push(unsigned index, const std::uint8_t* data,
                                  std::size_t bytes, std::int64_t first) {
  owner(); auto& q = side(index);
  if (phase_ != OwnerIngressPhase::committed) throw std::logic_error("Owner not committed");
  if (first != q.stats.nextWriteFrame) throw std::invalid_argument("Noncontiguous owner input");
  const auto count = validate(data, bytes, first, 1048576U / (channels_ * static_cast<unsigned>(encoding_)));
  const auto accepted = std::min(count, capacity_ - q.stats.queued);
  for (std::uint32_t i = 0; i < accepted; ++i) {
    const auto frame = (q.head + q.stats.queued + i) % capacity_;
    for (std::uint32_t c = 0; c < channels_; ++c)
      q.samples[static_cast<std::size_t>(frame) * channels_ + c] =
          sample(data + (static_cast<std::size_t>(i) * channels_ + c) * static_cast<unsigned>(encoding_));
  }
  q.stats.queued += accepted; q.stats.accepted += accepted; q.stats.nextWriteFrame += accepted;
  return accepted;
}
OwnerRead PcmOwnerIngress::read(unsigned index, float* dst, std::uint32_t count) {
  owner(); auto& q = side(index);
  if (phase_ != OwnerIngressPhase::committed) throw std::logic_error("Owner not committed");
  if (count > capacity_ || (count && !dst)) throw std::invalid_argument("Invalid owner read buffer");
  const auto first = q.stats.nextReadFrame;
  const bool prefix = first < q.stats.cueFrame;
  auto delivered = std::min(count, q.stats.queued);
  if (prefix) delivered = static_cast<std::uint32_t>(std::min<std::int64_t>(delivered, q.stats.cueFrame - first));
  for (std::uint32_t i = 0; i < delivered; ++i) {
    const auto frame = (q.head + i) % capacity_;
    for (std::uint32_t c = 0; c < channels_; ++c)
      dst[static_cast<std::size_t>(i)*channels_ + c] = q.samples[static_cast<std::size_t>(frame)*channels_ + c];
  }
  q.head = (q.head + delivered) % capacity_; q.stats.queued -= delivered;
  q.stats.read += delivered; q.stats.nextReadFrame += delivered;
  return {first, delivered, prefix};
}
OwnerQueueStats PcmOwnerIngress::stats(unsigned index) { owner(); return side(index).stats; }
OwnerIngressPhase PcmOwnerIngress::phase() { owner(); return phase_; }
}
