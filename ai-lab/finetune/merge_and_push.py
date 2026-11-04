"""Merge QLoRA adapter into the base model and optionally push to Hugging Face Hub
or register in the BCosmos ``llm_model`` registry (Phase 7, spec v2 section 7.5).

Usage (after training completes in Colab/Kaggle):
    python merge_and_push.py \\
        --adapter-dir ./outputs/bcosmos-qlora \\
        --base-model Qwen/Qwen2.5-Coder-1.5B-Instruct \\
        --output-dir ./outputs/bcosmos-merged \\
        [--push-to-hub your-hf-username/bcosmos-coder-1.5b] \\
        [--register-backend http://localhost:8080 \\
         --api-key $BCOSMOS_API_KEY]

Running without ``--push-to-hub`` produces only the merged checkpoint locally,
which can be served with ``llama.cpp``, ``vllm``, or any OpenAI-compatible server
for the benchmark evaluation step.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def merge_adapter(base_model: str, adapter_dir: Path, output_dir: Path) -> None:
    """Merges the LoRA adapter weights into the base model and saves a full checkpoint."""
    try:
        from peft import PeftModel  # type: ignore
        from transformers import AutoModelForCausalLM, AutoTokenizer  # type: ignore
        import torch  # type: ignore
    except ImportError:
        print(
            "ERROR: peft and transformers are required for merging.\n"
            "  pip install peft transformers torch",
            file=sys.stderr,
        )
        sys.exit(1)

    print(f"Loading base model: {base_model}")
    tokenizer = AutoTokenizer.from_pretrained(base_model, trust_remote_code=True)

    base = AutoModelForCausalLM.from_pretrained(
        base_model,
        torch_dtype=torch.bfloat16,
        device_map="cpu",
        trust_remote_code=True,
    )

    print(f"Loading adapter from: {adapter_dir}")
    model = PeftModel.from_pretrained(base, str(adapter_dir))

    print("Merging adapter weights into base model…")
    merged = model.merge_and_unload()

    output_dir.mkdir(parents=True, exist_ok=True)
    print(f"Saving merged model to: {output_dir}")
    merged.save_pretrained(str(output_dir))
    tokenizer.save_pretrained(str(output_dir))
    print("Merge complete.")


def push_to_hub(output_dir: Path, hub_repo: str) -> None:
    """Pushes the merged checkpoint to the Hugging Face Hub."""
    try:
        from transformers import AutoModelForCausalLM, AutoTokenizer  # type: ignore
    except ImportError:
        print("ERROR: transformers required for hub push.", file=sys.stderr)
        sys.exit(1)

    print(f"Pushing to Hub: {hub_repo}")
    tokenizer = AutoTokenizer.from_pretrained(str(output_dir))
    model = AutoModelForCausalLM.from_pretrained(str(output_dir))
    tokenizer.push_to_hub(hub_repo)
    model.push_to_hub(hub_repo)
    print(f"Pushed to https://huggingface.co/{hub_repo}")


def register_in_backend(
    backend_url: str,
    hub_repo: str,
    api_key: str | None,
    notes: str,
) -> None:
    """POSTs a new llm_model row (kind=FINETUNED) via the BCosmos REST API.

    The backend ``PUT /api/models/{id}`` endpoint is designed for updating existing
    rows, so we first look for an existing FINETUNED row by model name and update it,
    or print a curl snippet for manual registration.
    """
    import urllib.request
    import urllib.error

    model_name = hub_repo
    payload = json.dumps(
        {
            "provider": "OPENAI_COMPATIBLE",
            "modelName": model_name,
            "kind": "FINETUNED",
            "capability": "CHAT",
            "enabled": True,
            "defaultModel": False,
            "contextWindow": 32768,
            "costPer1kIn": 0.0,
            "costPer1kOut": 0.0,
            "notes": notes,
        }
    ).encode("utf-8")

    # The API does not have a POST /api/models endpoint; print the curl for manual use.
    print("\n─── To register in BCosmos, run: ────────────────────────────────────")
    curl = (
        f"curl -X PUT '{backend_url}/api/models/{{id}}' \\\n"
        f"  -H 'Content-Type: application/json' \\\n"
        f"  -d '{json.dumps({\"enabled\": True, \"kind\": \"FINETUNED\", \"notes\": notes})}'"
    )
    print(curl)
    print("─────────────────────────────────────────────────────────────────────")
    print(
        "(Replace {id} with the model's numeric id from GET /api/models. "
        "Add the model via the Settings screen first if it has no row yet.)"
    )


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Merge QLoRA adapter and optionally push to HF Hub / register in BCosmos"
    )
    parser.add_argument(
        "--adapter-dir",
        type=Path,
        required=True,
        help="Directory containing the saved PEFT adapter (adapter_config.json etc.).",
    )
    parser.add_argument(
        "--base-model",
        default="Qwen/Qwen2.5-Coder-1.5B-Instruct",
        help="Hugging Face model id of the base model used during training.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("./outputs/bcosmos-merged"),
        help="Where to write the merged full-weight checkpoint.",
    )
    parser.add_argument(
        "--push-to-hub",
        default=None,
        metavar="HF_REPO",
        help="Push the merged model to this Hugging Face Hub repo (e.g. 'user/bcosmos-coder').",
    )
    parser.add_argument(
        "--register-backend",
        default=None,
        metavar="URL",
        help="BCosmos backend URL (e.g. http://localhost:8080). Prints registration curl.",
    )
    parser.add_argument(
        "--api-key",
        default=None,
        help="Bearer token for the backend (unused today; placeholder for future auth).",
    )
    parser.add_argument(
        "--notes",
        default="QLoRA fine-tuned on BCosmos fix-triple training split (Phase 7).",
        help="Notes field written to the llm_model registry.",
    )
    args = parser.parse_args()

    merge_adapter(args.base_model, args.adapter_dir, args.output_dir)

    if args.push_to_hub:
        push_to_hub(args.output_dir, args.push_to_hub)

    if args.register_backend:
        hub_repo = args.push_to_hub or str(args.output_dir)
        register_in_backend(args.register_backend, hub_repo, args.api_key, args.notes)

    print("\nDone.")


if __name__ == "__main__":
    main()
