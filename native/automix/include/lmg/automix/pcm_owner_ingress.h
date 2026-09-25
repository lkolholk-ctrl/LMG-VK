#pragma once
#include <array>
#include <cstddef>
#include <cstdint>
#include <thread>
#include <vector>

namespace lmg::automix {
// Admission/ownership transport only. No audio device, gain, DSP, or clock authority.
// Constructor allocates all sample storage. Mutating methods are single-owner-thread.
enum class OwnerEncoding : unsigned { pcm16le = 2, float32le = 4 };
enum class OwnerIngressPhase : unsigned { empty, staged, committed, aborted };
struct OwnerInput {
  const std::uint8_t* data;
  std::size_t bytes;
  std::int64_t firstFrame, cueFrame;
};
struct OwnerRead {
  std::int64_t firstFrame = 0;
  std::uint32_t frames = 0;
  bool prefix = false; // One read NEVER spans the pre-cue/post-cue boundary.
};
struct OwnerQueueStats {
  std::int64_t nextReadFrame = 0, nextWriteFrame = 0, cueFrame = 0;
  std::uint64_t accepted = 0, read = 0, discarded = 0;
  std::uint32_t queued = 0;
};
class PcmOwnerIngress final {
 public:
  static constexpr std::uint32_t maximumCapacity = 262144;
  static constexpr std::int64_t maximumFrame = 9007199254740991LL;
  PcmOwnerIngress(std::uint32_t channels, OwnerEncoding encoding, std::uint32_t capacityFrames);
  // Copies/validates BOTH buffers into invisible staging. Originals stay untouched.
  // Any stage error leaves phase empty; no previous stage can be overwritten.
  void stage(std::uint64_t ticket, std::int64_t generation, std::int64_t revision,
             OwnerInput outgoing, OwnerInput incoming);
  // No allocation, no sample validation and no partial side publication here.
  bool commit(std::uint64_t ticket);
  // Precommit rollback -> empty. Postcommit discard -> terminal aborted, NOT replay.
  bool abort(std::uint64_t ticket);
  // Sequential frames only. Returns accepted prefix; backpressure returns zero.
  std::uint32_t push(unsigned side, const std::uint8_t* pcm, std::size_t bytes,
                     std::int64_t firstFrame);
  OwnerRead read(unsigned side, float* destination, std::uint32_t maximumFrames);
  OwnerQueueStats stats(unsigned side);
  OwnerIngressPhase phase();
  std::uint32_t channels() const noexcept { return channels_; }
  OwnerEncoding encoding() const noexcept { return encoding_; }
  std::uint32_t capacity() const noexcept { return capacity_; }
 private:
  struct Queue {
    std::vector<float> samples, staging;
    OwnerQueueStats stats;
    std::uint32_t head = 0, stagedFrames = 0;
    std::int64_t stagedFirst = 0, stagedCue = 0;
  };
  std::array<Queue, 2> queues_;
  std::uint32_t channels_, capacity_;
  OwnerEncoding encoding_;
  OwnerIngressPhase phase_ = OwnerIngressPhase::empty;
  std::thread::id owner_{};
  bool ownerBound_ = false;
  std::uint64_t ticket_ = 0;
  std::int64_t generation_ = 0, revision_ = 0;
  void owner();
  Queue& side(unsigned index);
  std::uint32_t validate(const std::uint8_t* data, std::size_t bytes,
                         std::int64_t first, std::uint32_t limit) const;
  float sample(const std::uint8_t* bytes) const noexcept;
};
}
