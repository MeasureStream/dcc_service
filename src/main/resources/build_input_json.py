# unisce  5 JSON in certificato_funzione_input.json, priorita job>client>method>base

from __future__ import annotations

import argparse
import json
import copy
from pathlib import Path

HERE = Path(__file__).resolve().parent


def load(name: str) -> dict:
    path = HERE / name
    return json.loads(path.read_text(encoding="utf-8"))


def strip_comments(obj):
    # toglie le chiavi _comment prima di scrivere output
    if isinstance(obj, dict):
        return {k: strip_comments(v) for k, v in obj.items() if k != "_comment"}
    if isinstance(obj, list):
        return [strip_comments(i) for i in obj]
    return obj


def merge(
    method_file: str = "calibration_method.json",
    client_file: str = "client_company.json",
    job_file: str = "job.json",
) -> dict:
    ms   = load("measurestream_company.json")
    meth = load(method_file)
    cl   = load(client_file)
    job  = load(job_file)
    base = load("base_input.json")

    # compania —  identita lab + document_id dal job
    company_data = copy.deepcopy(ms["company_data"])
    company_data["document_id"] = job["calibration_specific_data"]["document_id"]

    organization_data = copy.deepcopy(ms["organization_data"])
    organization_data.update(job.get("organization_data", {}))

    sensor_method_template = copy.deepcopy(meth["sensor_method_template"])
    sensor_method_template.update(job.get("sensor_method_template", {}))

    calibration_specific_data = copy.deepcopy(
        meth.get("calibration_specific_data_method", {})
    )
    calibration_specific_data.update(cl["calibration_specific_data"])
    calibration_specific_data.update(job["calibration_specific_data"])
    calibration_specific_data.update(
        base.get("calibration_specific_data_computed", {})
    )

    obs_base   = base["calculated_calibration_values"].get("observations_base", [])
    obs_method = meth.get("calculated_calibration_values_method", {}).get(
        "observations_method", []
    )
    conclusions = meth["calculated_calibration_values_method"]["conclusions"]

    calculated_calibration_values = {
        "measurements": copy.deepcopy(
            base["calculated_calibration_values"]["measurements"]
        ),
        "observations": obs_base + obs_method,
        "conclusions": conclusions,
    }

    pdf_template_data = copy.deepcopy(base.get("pdf_template_data_base", {}))

    stmt_base   = pdf_template_data.pop("statements_base", [])
    stmt_method = meth.get("pdf_template_data_method", {}).get("statements_method", [])
    pdf_template_data["statements"] = stmt_base + stmt_method

    for key, value in meth.get("pdf_template_data_method", {}).items():
        if key != "statements_method":
            pdf_template_data[key] = value

    cert_id = job["calibration_specific_data"]["certificate_id"]
    pdf_template_data["footer_left_text"] = (
        f"{ms['company_data']['department']} \u2014 Calibration certificate {cert_id}"
    )

    calibration_result = copy.deepcopy(base["_calibration_result"])

    result = {
        "template_parts": {
            "company_data": company_data,
            "organization_data": organization_data,
            "sensor_method_template": sensor_method_template,
            "calibration_specific_data": calibration_specific_data,
            "calculated_calibration_values": calculated_calibration_values,
            "pdf_template_data": pdf_template_data,
        },
        "_calibration_result": calibration_result,
    }
    return strip_comments(result)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build certificato_funzione_input.json from source JSONs."
    )
    parser.add_argument(
        "--method",
        default="calibration_method.json",
        help="Method/instrument JSON (default: calibration_method.json)",
    )
    parser.add_argument(
        "--client",
        default="client_company.json",
        help="Client identity JSON (default: client_company.json)",
    )
    parser.add_argument(
        "--job",
        default="job.json",
        help="Per-job data JSON (default: job.json)",
    )
    parser.add_argument(
        "--out",
        default=str(HERE / "certificato_funzione_input.json"),
        help="Output path (default: template_in/certificato_funzione_input.json)",
    )
    args = parser.parse_args()

    merged = merge(
        method_file=args.method,
        client_file=args.client,
        job_file=args.job,
    )
    out_path = Path(args.out)
    out_path.write_text(
        json.dumps(merged, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    print(f"Written: {out_path}")


if __name__ == "__main__":
    main()
